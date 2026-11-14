import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse } from 'acorn'

// 校验生成的微信代码，而非只检查 Vue 源码或某个工具文件是否存在。
export function verifyWechatBuild(directory) {
  const root = path.resolve(directory)
  const files = []
  function collect(folder) {
    for (const entry of fs.readdirSync(folder, { withFileTypes: true })) {
      const file = path.join(folder, entry.name)
      if (entry.isDirectory()) collect(file)
      else if (entry.name.endsWith('.js')) files.push(file)
    }
  }
  collect(root)
  if (!files.length) throw Error('微信构建目录中没有 JavaScript 文件')
  let dependencies = 0
  for (const file of files) {
    const name = path.relative(root, file)
    let tree
    try { tree = parse(fs.readFileSync(file, 'utf8'), { ecmaVersion: 2017, sourceType: 'script' }) }
    catch (error) { throw Error(`${name} 包含构建目标 ES2017 不支持的语法：${error.message}`) }
    function visit(node) {
      if (!node || typeof node !== 'object') return
      if (node.type === 'CallExpression' && node.callee.type === 'Identifier' && node.callee.name === 'require') {
        const argument = node.arguments[0]
        if (argument?.type !== 'Literal' || typeof argument.value !== 'string') throw Error(`${name} 使用了无法静态核对的 require`)
        const reference = argument.value
        const target = path.resolve(reference.startsWith('.') ? path.dirname(file) : root, reference)
        if (!target.startsWith(root + path.sep)) throw Error(`${name} 的模块引用越出构建目录：${reference}`)
        if (![target, target + '.js', path.join(target, 'index.js')].some(p => fs.existsSync(p) && fs.statSync(p).isFile())) {
          throw Error(`${name} 引用的模块未打包：${reference}`)
        }
        dependencies++
      }
      for (const value of Object.values(node)) {
        if (Array.isArray(value)) value.forEach(visit)
        else if (value && typeof value === 'object') visit(value)
      }
    }
    visit(tree)
  }
  const config = JSON.parse(fs.readFileSync(path.join(root, 'project.config.json'), 'utf8'))
  if (Boolean(config.setting?.es6) !== Boolean(config.setting?.enhance)) throw Error('微信项目 es6 与 enhance 编译设置不一致')
  return { files: files.length, dependencies }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = process.argv[2] || fileURLToPath(new URL('../dist/build/mp-weixin', import.meta.url))
  const result = verifyWechatBuild(root)
  console.log(`微信构建校验通过：${result.files} 个 JS 文件，${result.dependencies} 处模块依赖，ES2017 语法及编译开关一致。`)
}
