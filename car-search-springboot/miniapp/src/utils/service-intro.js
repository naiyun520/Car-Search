/**
 * 服务介绍与展示配置
 * 业务文案依据：项目根目录《api接口详情.md》、《汽修宝接口爬虫.md》及后端服务目录
 * 结构：code -> { title 展示名, intro 一句话介绍, tags 亮点标签, display 结果页呈现类型 }
 * display: verify=核验结论型 | profile=车辆档案型 | stat=统计数据型 | generic=通用字段型
 */
export const SERVICE_INTRO = {
  vin_decode: {
    title: 'VIN码车辆信息解析',
    intro: '通过 17 位车架号解码车辆型号、排量、驱动方式、动力参数与排放标准，还原车辆出厂配置。',
    tags: ['VIN 解码', '配置解析', '动力参数'],
    display: 'profile'
  },
  vin_plate: {
    title: 'VIN车架号反查车牌号',
    intro: '通过车架号反查对应车牌号码与车辆初次登记日期，用于车辆身份交叉核验。',
    tags: ['反查车牌', '身份核验'],
    display: 'profile'
  },
  plate_basic: {
    title: '车牌五项信息',
    intro: '查询车辆品牌、车架号 VIN、车辆种类、初登日期与使用性质，建立车辆基础档案。',
    tags: ['基础档案', '品牌车型'],
    display: 'profile'
  },
  plate_full: {
    title: '车牌综合信息',
    intro: '一次查询覆盖车辆基础档案与车型配置共 20+ 项参数，含排量、驱动、变速箱与市场参考价。',
    tags: ['综合档案', '20+ 项参数'],
    display: 'profile'
  },
  owned_vehicle_count: {
    title: '名下车辆数量',
    intro: '通过身份证号或公司统一社会信用代码，核验个人或企业名下登记车辆总数。',
    tags: ['名下车辆', '归属核验'],
    display: 'stat'
  },
  owner_verify: {
    title: '人车关系核验',
    intro: '核验指定姓名与车牌号是否为同一车辆的登记关系，输出一致性结论。',
    tags: ['一致性核验', '人车匹配'],
    display: 'verify'
  },
  owner_verify_precise: {
    title: '人车关系核验V2',
    intro: '支持车牌号或 VIN 双通道核验，对姓名与车辆关系进行更精准的一致性确认。',
    tags: ['精准核验', '双通道'],
    display: 'verify'
  },
  transfer_count: {
    title: '车辆过户次数',
    intro: '查询车辆历史过户次数，辅助掌握车辆流转与交易背景。',
    tags: ['过户记录', '交易背景'],
    display: 'stat'
  },
  insurance_dates: {
    title: '车辆上险信息查询',
    intro: '查询车辆初次上险与最新交强险投保时间，了解车辆投保延续情况。',
    tags: ['上险记录', '投保时间'],
    display: 'stat'
  },
  etc_owner_verify: {
    title: '人车实名检验-ETC',
    intro: '核验指定人员是否为指定车辆 ETC 登记所有人，输出实名一致性结论。',
    tags: ['ETC 核验', '实名一致'],
    display: 'verify'
  },
  owned_etc_count: {
    title: '名下ETC车辆总数',
    intro: '通过证件号码查询个人名下 ETC 车辆登记总数。',
    tags: ['ETC 数量', '名下登记'],
    display: 'stat'
  }
}

/** 获取服务展示配置（不存在时回退为通用型） */
export function serviceIntro(service) {
  const base = SERVICE_INTRO[service.code] || {}
  return {
    title: base.title || service.name || service.short_name || '',
    intro: base.intro || service.description || '',
    tags: base.tags || [],
    display: base.display || 'generic'
  }
}
