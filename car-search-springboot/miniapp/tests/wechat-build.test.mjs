import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { verifyWechatBuild } from '../scripts/verify-wechat-build.mjs'

function fixture(t, files, setting = { es6: true, enhance: true }) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'car-wechat-build-'))
  t.after(() => {
    const target = path.resolve(root)
    assert.equal(path.dirname(target), path.resolve(os.tmpdir()))
    assert.match(path.basename(target), /^car-wechat-build-/)
    fs.rmSync(target, { recursive: true, force: true })
  })
  for (const [name, code] of Object.entries(files)) {
    const file = path.join(root, name)
    fs.mkdirSync(path.dirname(file), { recursive: true })
    fs.writeFileSync(file, code)
  }
  fs.writeFileSync(path.join(root, 'project.config.json'), JSON.stringify({ setting }))
  return root
}
test('generated page must include its relative report helper', t => {
  const root = fixture(t, { 'pages/result/result.js': 'require("../../utils/report-loader.js")' })
  assert.throws(() => verifyWechatBuild(root), /引用的模块未打包.*report-loader/)
  fs.mkdirSync(path.join(root, 'utils'))
  fs.writeFileSync(path.join(root, 'utils/report-loader.js'), 'exports.createReportLoader=function(){}')
  assert.equal(verifyWechatBuild(root).dependencies, 1)
})
test('generated syntax cannot exceed the declared compatibility target', t => {
  const root = fixture(t, { 'utils/report-loader.js': 'try{}catch{}' })
  assert.throws(() => verifyWechatBuild(root), /ES2017/)
})
test('module references cannot escape the mini program package', t => {
  const root = fixture(t, { 'app.js': 'require("../outside.js")' })
  assert.throws(() => verifyWechatBuild(root), /越出构建目录/)
})
test('generated WeChat compiler switches must agree', t => {
  const root = fixture(t, { 'app.js': '"use strict";' }, { es6: true })
  assert.throws(() => verifyWechatBuild(root), /编译设置不一致/)
})
