#!/usr/bin/env python3
"""Mirror the official Weixin Mini Program documentation as Markdown."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import time
from collections import deque
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from urllib.parse import quote, unquote, urljoin, urlsplit, urlunsplit

import requests
from bs4 import BeautifulSoup, Tag
from markdownify import MarkdownConverter


BASE_URL = "https://developers.weixin.qq.com"
CATEGORIES = {
    "开发": "/miniprogram/dev/",
    "介绍": "/miniprogram/introduction/",
    "设计": "/miniprogram/design/",
    "运营": "/miniprogram/product/",
    "数据": "/miniprogram/analysis/",
    "安全": "/miniprogram/security/",
}
SEEDS = {
    "开发": "/miniprogram/dev/framework/",
    "介绍": "/miniprogram/introduction/",
    "设计": "/miniprogram/design/",
    "运营": "/miniprogram/product/",
    "数据": "/miniprogram/analysis/wedata/intro/",
    "安全": "/miniprogram/security/basic/",
}
SKIP_SUFFIXES = {
    ".7z", ".apk", ".css", ".csv", ".doc", ".docx", ".gif", ".gz",
    ".ico", ".jpeg", ".jpg", ".js", ".json", ".mp3", ".mp4", ".pdf",
    ".png", ".rar", ".svg", ".tar", ".txt", ".webm", ".webp", ".xls",
    ".xlsx", ".xml", ".zip",
}
ASSET_SUFFIXES = {".gif", ".jpeg", ".jpg", ".png", ".svg", ".webp"}


class WeixinMarkdownConverter(MarkdownConverter):
    def convert_pre(self, el, text, parent_tags):
        code = el.find("code")
        raw = code.get_text("", strip=False) if code else el.get_text("", strip=False)
        language = ""
        if code:
            classes = code.get("class", [])
            match = next((re.match(r"(?:language-|lang-)([\w+-]+)", c) for c in classes), None)
            if match:
                language = match.group(1)
        fence = "```"
        while fence in raw:
            fence += "`"
        return f"\n\n{fence}{language}\n{raw.rstrip()}\n{fence}\n\n"


def clean_url(value: str, base: str = BASE_URL) -> str | None:
    value = value.strip()
    if not value or value.startswith(("javascript:", "mailto:", "tel:")):
        return None
    absolute = urljoin(base, value)
    parsed = urlsplit(absolute)
    if parsed.scheme not in {"http", "https"} or parsed.netloc != "developers.weixin.qq.com":
        return None
    path = re.sub(r"/{2,}", "/", unquote(parsed.path)).strip()
    if path == "/miniprogram/dev/":
        path = SEEDS["开发"]
    if "/cooperation_shop/" in path:
        parts = [part for part in path.split("/") if part]
        # Several cooperation-shop pages contain broken relative links that
        # recursively append these directories while still returning HTTP 200.
        if parts.count("basic") > 1 or parts.count("cooperation_gift") > 3:
            return None
    if path.endswith("/index.html"):
        path = path[:-10]
    if path.startswith("/miniprogram/en/"):
        return None
    suffix = PurePosixPath(path).suffix.lower()
    if suffix in SKIP_SUFFIXES:
        return None
    return urlunsplit(("https", parsed.netloc, path, "", ""))


def category_for(url: str, enabled: dict[str, str]) -> str | None:
    path = urlsplit(url).path
    for name, prefix in enabled.items():
        if path.startswith(prefix):
            return name
    return None


def output_path(url: str, root: Path) -> Path:
    path = urlsplit(url).path.strip("/")
    if not path:
        path = "index"
    if path.endswith(".html"):
        path = path[:-5]
    elif urlsplit(url).path.endswith("/"):
        path += "/index"
    parts = PurePosixPath(path).parts
    return root.joinpath(*parts[:-1], parts[-1] + ".md")


def relative_doc_link(source_url: str, target_url: str, root: Path, fragment: str = "") -> str:
    source = output_path(source_url, root).parent
    target = output_path(target_url, root)
    relative = Path(os.path.relpath(target, source)).as_posix()
    return relative + (f"#{fragment}" if fragment else "")


def safe_asset_name(url: str) -> str:
    parsed = urlsplit(url)
    suffix = PurePosixPath(parsed.path).suffix.lower()
    if suffix not in ASSET_SUFFIXES:
        suffix = ".bin"
    stem = re.sub(r"[^0-9A-Za-z._-]+", "-", PurePosixPath(parsed.path).stem).strip("-._")[:60]
    digest = hashlib.sha256(url.encode("utf-8")).hexdigest()[:12]
    return f"{digest}-{stem or 'asset'}{suffix}"


def request(session: requests.Session, url: str, retries: int = 3) -> requests.Response:
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            response = session.get(url, timeout=(10, 45), allow_redirects=True)
            if response.status_code == 429 or response.status_code >= 500:
                raise requests.HTTPError(f"HTTP {response.status_code}", response=response)
            if 400 <= response.status_code < 500:
                response.raise_for_status()  # permanent page error: do not retry
            response.raise_for_status()
            return response
        except requests.RequestException as error:
            last_error = error
            if error.response is not None and 400 <= error.response.status_code < 500 and error.response.status_code != 429:
                break
            if attempt + 1 < retries:
                time.sleep(2 ** attempt)
    raise RuntimeError(f"download failed: {url}: {last_error}")


def article_from(html: bytes) -> tuple[BeautifulSoup, Tag, str]:
    soup = BeautifulSoup(html, "html.parser")
    article = soup.select_one(".content.custom") or soup.select_one("main.page")
    if not article:
        raise ValueError("document body not found")
    for selector in (
        "script", "style", "noscript", ".page-edit", ".page-nav", ".feedback",
        ".header-anchor", ".code-copy", "button", "svg[aria-hidden=true]",
    ):
        for node in article.select(selector):
            node.decompose()
    title_node = article.find(["h1", "h2"])
    title = title_node.get_text(" ", strip=True) if title_node else ""
    if not title and soup.title:
        title = soup.title.get_text(" ", strip=True).split(" | ", 1)[0]
    return soup, article, title or "未命名文档"


def mirror_asset(
    session: requests.Session, asset_url: str, output_root: Path, page_file: Path
) -> str:
    assets = output_root / "_assets"
    assets.mkdir(parents=True, exist_ok=True)
    target = assets / safe_asset_name(asset_url)
    if not target.exists():
        response = request(session, asset_url)
        if len(response.content) > 20 * 1024 * 1024:
            raise ValueError("asset exceeds 20 MiB")
        target.write_bytes(response.content)
    return Path(os.path.relpath(target, page_file.parent)).as_posix()


def convert_page(
    session: requests.Session,
    url: str,
    response: requests.Response,
    enabled: dict[str, str],
    output_root: Path,
    download_assets: bool,
) -> tuple[str, str, set[str], list[str]]:
    _, article, title = article_from(response.content)
    discovered: set[str] = set()
    warnings: list[str] = []
    page_file = output_path(url, output_root)

    for anchor in article.select("a[href]"):
        raw = anchor.get("href", "")
        parsed_raw = urlsplit(urljoin(url, raw))
        target = clean_url(raw, url)
        if target and category_for(target, enabled):
            discovered.add(target)
            anchor["href"] = relative_doc_link(url, target, output_root, parsed_raw.fragment)
        elif raw.startswith("/"):
            anchor["href"] = urljoin(BASE_URL, raw)

    if download_assets:
        for image in article.select("img[src]"):
            asset_url = urljoin(url, image.get("src", ""))
            parsed = urlsplit(asset_url)
            if parsed.scheme not in {"http", "https"}:
                continue
            try:
                image["src"] = mirror_asset(session, asset_url, output_root, page_file)
            except Exception as error:  # retain remote image if mirroring fails
                warnings.append(f"asset {asset_url}: {error}")
                image["src"] = asset_url

    markdown = WeixinMarkdownConverter(
        heading_style="ATX",
        bullets="-",
        strip=["span"],
    ).convert_soup(article)
    markdown = re.sub(r"\n{4,}", "\n\n\n", markdown).strip() + "\n"
    fetched = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")
    header = (
        "---\n"
        f'title: {json.dumps(title, ensure_ascii=False)}\n'
        f'source: {json.dumps(url, ensure_ascii=False)}\n'
        f'fetched_at: {json.dumps(fetched)}\n'
        "---\n\n"
    )
    return title, header + markdown, discovered, warnings


def load_manifest(path: Path) -> dict:
    if not path.exists():
        return {"version": 1, "pages": {}, "errors": {}}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"version": 1, "pages": {}, "errors": {}}


def save_manifest(path: Path, manifest: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def write_index(output_root: Path, manifest: dict, enabled: dict[str, str]) -> None:
    grouped: dict[str, list[tuple[str, str]]] = {name: [] for name in enabled}
    indexed_paths: set[str] = set()
    for url, item in manifest.get("pages", {}).items():
        category = category_for(url, enabled)
        if category:
            path = output_path(url, output_root)
            link = Path(os.path.relpath(path, output_root)).as_posix()
            if link in indexed_paths:
                continue
            indexed_paths.add(link)
            grouped[category].append((item.get("title") or url, link))

    lines = [
        "# 微信小程序官方文档（本地镜像）",
        "",
        f"> 来源：[{BASE_URL}/miniprogram/]({BASE_URL}/miniprogram/)",
        "> 本目录由脚本自动生成，仅供项目开发检索；微信官方在线文档为最终依据。",
        "",
        f"页面总数：{sum(len(items) for items in grouped.values())}",
        "",
    ]
    for category, items in grouped.items():
        lines.extend([f"## {category}（{len(items)}）", ""])
        for title, link in sorted(items, key=lambda item: item[1].lower()):
            lines.append(f"- [{title}]({link})")
        lines.append("")
    if manifest.get("errors"):
        lines.extend([
            "## 抓取失败记录",
            "",
            "失败页面保存在 `_manifest.json` 的 `errors` 字段，可再次运行脚本重试。",
            "",
        ])
    (output_root / "README.md").write_text("\n".join(lines), encoding="utf-8")


def prune_orphan_markdown(output_root: Path, manifest: dict) -> int:
    keep = {
        (output_root / item["path"]).resolve()
        for item in manifest.get("pages", {}).values()
        if item.get("path")
    }
    keep.update({(output_root / "README.md").resolve(), (output_root / "使用说明.md").resolve()})
    removed = 0
    for path in output_root.rglob("*.md"):
        if path.resolve() not in keep:
            path.unlink()
            removed += 1
    return removed


def repair_broken_local_links(output_root: Path) -> int:
    pattern = re.compile(r"(?P<prefix>!?)\[(?P<label>[^]]*)\]\((?P<target>[^)]+\.md(?:#[^)]*)?)\)")
    repaired = 0
    for page in output_root.rglob("*.md"):
        original = page.read_text(encoding="utf-8")

        def replace(match: re.Match[str]) -> str:
            nonlocal repaired
            target = match.group("target")
            if "://" in target or target.startswith("#"):
                return match.group(0)
            path_part, separator, fragment = target.partition("#")
            resolved = (page.parent / unquote(path_part)).resolve()
            if resolved.is_file() or output_root.resolve() not in resolved.parents:
                return match.group(0)
            try:
                relative = resolved.relative_to(output_root.resolve()).as_posix()
            except ValueError:
                return match.group(0)
            if not relative.endswith(".md"):
                return match.group(0)
            official_path = relative[:-3]
            if official_path.endswith("/index"):
                official_path = official_path[:-5]
            else:
                official_path += ".html"
            official = f"{BASE_URL}/{quote(official_path, safe='/._-')}"
            if separator:
                official += "#" + fragment
            repaired += 1
            return f"{match.group('prefix')}[{match.group('label')}]({official})"

        updated = pattern.sub(replace, original)
        if updated != original:
            page.write_text(updated, encoding="utf-8")
    return repaired


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("微信小程序官方文档"))
    parser.add_argument("--category", action="append", choices=CATEGORIES, help="只抓指定分类，可重复")
    parser.add_argument("--delay", type=float, default=0.35, help="每个页面之间的等待秒数")
    parser.add_argument("--max-pages", type=int, default=0, help="本次最多处理页面数，0表示不限")
    parser.add_argument("--no-assets", action="store_true", help="保留远程图片地址，不下载图片")
    parser.add_argument("--refresh", action="store_true", help="重新转换已经成功保存的页面")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.delay < 0.2:
        raise SystemExit("--delay 不能小于0.2秒")
    enabled = {name: prefix for name, prefix in CATEGORIES.items() if not args.category or name in args.category}
    output_root = args.output.resolve()
    output_root.mkdir(parents=True, exist_ok=True)
    manifest_path = output_root / "_manifest.json"
    manifest = load_manifest(manifest_path)
    for section in ("pages", "errors"):
        for stale_url in list(manifest.get(section, {})):
            if clean_url(stale_url) is None:
                manifest[section].pop(stale_url, None)
    for stale_url in list(manifest.get("errors", {})):
        if clean_url(stale_url) != stale_url:
            manifest["errors"].pop(stale_url, None)
    initial_urls = [clean_url(SEEDS[name]) for name in enabled]
    for item in manifest.get("pages", {}).values():
        initial_urls.extend(clean_url(link) for link in item.get("links", []))
    permanent_errors = {
        url for url, item in manifest.get("errors", {}).items()
        if "404 Client Error" in item.get("error", "")
    }
    queue = deque(dict.fromkeys(
        url for url in initial_urls
        if url and category_for(url, enabled) and (args.refresh or url not in permanent_errors)
    ))
    queued = set(queue)
    completed = {
        url for url, item in manifest.get("pages", {}).items()
        if not args.refresh
        and item.get("path") == output_path(url, output_root).relative_to(output_root).as_posix()
        and output_path(url, output_root).is_file()
    }
    processed = 0

    session = requests.Session()
    session.headers.update({
        "User-Agent": "CarSearch-WeixinDocsMirror/1.0 (+local development reference)",
        "Accept": "text/html,application/xhtml+xml,image/avif,image/webp,image/*,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9",
    })

    while queue and (not args.max_pages or processed < args.max_pages):
        url = queue.popleft()
        if not url or url in completed:
            continue
        try:
            response = request(session, url)
            content_type = response.headers.get("content-type", "")
            if "text/html" not in content_type:
                raise ValueError(f"unexpected content type: {content_type}")
            title, markdown, links, warnings = convert_page(
                session, url, response, enabled, output_root, not args.no_assets
            )
            target = output_path(url, output_root)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(markdown, encoding="utf-8")
            manifest.setdefault("pages", {})[url] = {
                "title": title,
                "path": target.relative_to(output_root).as_posix(),
                "fetched_at": datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds"),
                "links": sorted(links),
                "warnings": warnings,
            }
            manifest.setdefault("errors", {}).pop(url, None)
            completed.add(url)
            for link in sorted(links):
                if link not in queued and link not in completed:
                    queue.append(link)
                    queued.add(link)
            processed += 1
            print(f"[{processed}] {title} <- {url}", flush=True)
        except Exception as error:
            manifest.setdefault("errors", {})[url] = {
                "error": str(error),
                "updated_at": datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds"),
            }
            print(f"[ERROR] {url}: {error}", file=sys.stderr, flush=True)
        save_manifest(manifest_path, manifest)
        write_index(output_root, manifest, enabled)
        if queue:
            time.sleep(args.delay)

    manifest["last_run_at"] = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")
    manifest["queued_remaining"] = len(queue)
    manifest["orphan_markdown_removed"] = prune_orphan_markdown(output_root, manifest)
    manifest["broken_local_links_repaired"] = repair_broken_local_links(output_root)
    save_manifest(manifest_path, manifest)
    write_index(output_root, manifest, enabled)
    print(f"done: processed={processed}, total={len(manifest.get('pages', {}))}, remaining={len(queue)}")
    return 0 if not manifest.get("errors") else 2


if __name__ == "__main__":
    raise SystemExit(main())
