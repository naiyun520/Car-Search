import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { runInNewContext } from 'node:vm'
import { computed, ref } from 'vue'

test('actual result-page computed rules show completion and placeholder fields for successful empty transfer data', async () => {
  const source = await readFile(new URL('../src/pages/result/result.vue', import.meta.url), 'utf8')
  const missing = source.match(/const isMissing=computed\([^\n]+/)[0]
  const fields = source.slice(source.indexOf('const fieldGroups = computed'), source.indexOf('/* ===== 车辆参数档案'))
  const arrays = source.match(/const arrayGroups = computed\([^\n]+/)[0]
  const constants = [...source.matchAll(/const (?:STAT_KEYS|CONCLUSION_KEYS) = new Set\([^\n]+/g)].map(x => x[0]).join('\n')
  const context = {
    computed,
    order: ref({ status: 'success', result: [
      { key: 'order_status', label: '订单状态', value: '查询成功' },
      { key: 'data[]', label: '过户记录', display: 'array', value: '该车辆目前暂未出现过户记录', columns: [] }
    ] }),
    profile: ref(null), brandCard: ref(null), insurance: ref(null), claimReport: ref(null),
    hasResultSuffix: () => false, SAFETY_KEY_SUFFIXES: [], ADVICE_KEY_SUFFIXES: [],
    isPlainObject: v => v !== null && typeof v === 'object' && !Array.isArray(v),
    IMAGE_URL_RE: /^https?:\/\//, DECODE_KEY: 'decode_data[]'
  }
  runInNewContext(constants + '\n' + missing + '\n' + fields + '\n' + arrays +
    '\nresult={missing:isMissing.value,fields:fieldGroups.value,arrays:arrayGroups.value}', context)
  assert.equal(context.result.missing, false)
  assert.deepEqual(context.result.fields.map(x => x.value), ['查询成功', '该车辆目前暂未出现过户记录'])
  assert.equal(context.result.arrays.length, 0)
})
