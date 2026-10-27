const BUILTIN = new Set(['car','vin','plate','verified','location','transfer','owner','etc','insurance','search','document','shield','report'])

/* 服务 code -> 业务关键字图标：优先于数据库 builtin 图标 */
const CODE_KEYWORD = {
  // 车牌
  carnumber:'plate', plateparseseven:'plate', plate_basic:'plate', plate_full:'plate', plate_location:'plate',
  // 车架号 / VIN
  vindecode:'vin', vinparseplate:'vin', decode_normal:'vin', vin_decode:'vin', vin_plate:'vin',
  // 人车核验
  verify:'verified', owner_verify:'verified', owner_verify_precise:'verified',
  // 车主归属
  caranalysisperson:'owner', caranalysiscompany:'owner', owned_vehicle_count:'owner',
  // 保险
  compulsory_time:'insurance', end_date:'insurance', insurance_dates:'insurance', insurance_order:'insurance',
  // 过户
  transfer_count:'transfer', transfer_order:'transfer',
  // ETC
  etc_owner_verify:'etc', owned_etc_count:'etc',
  // 报告 / 记录
  claim_assessment_report:'report', history_order:'report', get_detail:'report',
  // 综合车况及其他
  car_decode:'car', create_order:'car', energy_create_order:'car'
}

/* 未显式登记的 code 按关键字片段推导，顺序即优先级（vin 必须先于 plate） */
const CODE_RULES = [
  ['vin', 'vin'],
  ['plate', 'plate'],
  ['carnumber', 'plate'],
  ['verify', 'verified'],
  ['owner', 'owner'],
  ['insurance|compulsory|end_date', 'insurance'],
  ['transfer', 'transfer'],
  ['etc', 'etc'],
  ['report|history|detail', 'report']
]

function keywordOfCode(code) {
  const value = String(code || '')
  if (CODE_KEYWORD[value]) return CODE_KEYWORD[value]
  for (const [pattern, keyword] of CODE_RULES) {
    if (new RegExp(pattern).test(value)) return keyword
  }
  return ''
}

export function serviceIconPath(icon) {
  const key=String(icon||'').replace(/^builtin:/,'')
  return BUILTIN.has(key) ? `/static/icons/services/${key}.svg` : ''
}

export function uploadedServiceIcon(icon, apiBaseUrl) {
  const filename=String(icon||'')
  return /^[a-f0-9]{32}\.(?:png|jpg|webp)$/.test(filename) ? `${apiBaseUrl}/service-icon/${filename}` : ''
}

/* 优先级：上传图片 > 服务 code 业务关键字 > 数据库 builtin key > car 兜底 */
export function serviceIcon(icon, apiBaseUrl, code) {
  return uploadedServiceIcon(icon,apiBaseUrl)
    || (keywordOfCode(code) ? `/static/icons/services/${keywordOfCode(code)}.svg` : '')
    || serviceIconPath(icon)
    || '/static/icons/services/car.svg'
}
