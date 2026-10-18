-- 修复旧服务的 provider_api_id
-- 旧服务使用 request_url_cipher 直连模式，provider_api_id 是旧系统遗留值
-- 将其重置为 0，使用直接 URL 模式

UPDATE ci_service
SET provider_api_id = 0
WHERE provider_api_id > 0
  AND (api_path IS NULL OR api_path = '')
  AND request_url_cipher IS NOT NULL
  AND request_url_cipher != '';

-- 验证修复结果
SELECT id, code, name, provider_api_id, api_path,
       CASE
         WHEN provider_api_id = 0 THEN '直接模式'
         WHEN provider_api_id > 0 THEN '供应商模式'
       END AS mode
FROM ci_service
ORDER BY id;