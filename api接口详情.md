车牌号查询车五项信息(车架号、VIN码、车辆种类、车辆品牌、车辆初登日期）+使用性质
 2

 GET/POST

 JSON

 支持

 ￥0.45/次

 https://www.yuanxiapi.cn/api/

 https://www.yuanxiapi.cn/api/?id=2&key=请在后台配置&chepai=车牌号

开发文档
 
错误码参照
 
示例代码
参数说明

参数	必填	说明
id	是	接口ID
key	是	对接秘钥
chepai	是	车牌号
返回参数

名称	说明
name	品牌名称
vin	vin车架号
date	初登日期
type	车辆种类
usage	使用性质
engine_no	发动机号
model_no	车辆型号
code	200:成功; 计费
user_info	用户信息; zid:用户ID; cost:单价;rmb:余额; login_time:最近登录;
返回示例

{
    "code": 200,
    "msg": "success",
    "data": {
        "name": "保时捷",
        "type": "乘用车",
        "vin": "WP0AB2978PL130034",
        "date": "2023-01",
        "usage": "非营运",
        "model_no": "WP0AB297",
        "engine_no": ""
    },
    "user_info": {
        "zid": "5",
        "rmb": "1.90300",
        "login_time": "2025-02-22 03:18:02"
    }
}




车架号/VIN码在线解析车辆相关信息接口
 11

 GET/POST

 JSON

 支持

 ￥0.043/次

 https://www.yuanxiapi.cn/api/

 https://www.yuanxiapi.cn/api/?id=11&key=请在后台配置&vin=vin车架号

开发文档
 
错误码参照
 
示例代码
参数说明

参数	必填	说明
id	是	接口ID
key	是	对接秘钥
vin	是	vin车架号
返回参数

名称	说明
vin	vin车架号
name_info	汽车型号
pailiang	车辆排量
market_price	市场价格
fuel_type	燃油类型
drivemode	驱动方式
che_id	车型ID
door_num	车门数量
seat_num	座位数量
cylinder_num	气缸数量
gear_type	变速箱类型
fuelmethod	供油方式
max_power	最大功率
max_horsepower	最大马力
enginemodel	发动机号
environmentalstandards	排放标准
code	200:成功; 计费
user_info	用户信息; zid:用户ID; cost:单价;rmb:余额; login_time:最近登录;
返回示例

{
    "code": 200,
    "msg": "success",
    "data": {
        "vin": "WP0AB2978PL130045",
        "name_info": "Panamera 2023款 Panamera 4S 2.9T",
        "pailiang": "2.9T",
        "market_price": "121.31万起",
        "fuel_type": "汽油",
        "drivemode": "前置四驱",
        "che_id": 162293,
        "door_num": "5",
        "seat_num": "4",
        "cylinder_num": "6",
        "gear_type": "湿式双离合变速箱(DCT)",
        "fuelmethod": "直喷",
        "max_power": "324",
        "max_horsepower": "441",
        "enginemodel": "CSZ",
        "environmentalstandards": "国VI"
    },
    "user_info": {
        "zid": "6",
        "rmb": "0.95000",
        "login_time": "2025-02-22 12:43:03"
    }
}



车牌解析车辆多项信息（所查信息包括：车辆品牌、车辆型号、发动机型号、车辆类型、车架号VIN、使用性质、初登日期、汽车排量、汽车马力、功率、轴数、轴距等基础信息）
提示、新上牌没超过半年、车牌发生过户但交强险没过户，或者过户信息未更新，这种情况的是过户前的信息（比如去年12月的车今年9月份过户，但交强险未过户，需要等到今年的12月重新购买交强险采集到数据进行更新。）介意者请不要查询，查询成功后概不退款。
 7

 GET/POST

 JSON

 支持

 ￥0.48/次

 https://www.yuanxiapi.cn/api/

 https://www.yuanxiapi.cn/api/?id=7&key=请在后台配置&chepai=车牌号

开发文档
 
错误码参照
 
示例代码
参数说明

参数	必填	说明
id	是	接口ID
key	是	对接秘钥
chepai	是	车牌号
返回参数

名称	说明
name	品牌名称
vin	vin车架号
date	初登日期
type	车辆种类
usage	使用性质
engine_no	发动机号
model_no	车辆型号
name_info	汽车型号
pailiang	车辆排量
market_price	市场价格
fuel_type	燃油类型
drivemode	驱动方式
che_id	车型ID
door_num	车门数量
seat_num	座位数量
cylinder_num	气缸数量
gear_type	变速箱类型
fuelmethod	供油方式
max_power	最大功率
max_horsepower	最大马力
enginemodel	发动机号
environmentalstandards	排放标准
code	200:成功; 计费
user_info	用户信息; zid:用户ID; cost:单价;rmb:余额; login_time:最近登录;
返回示例

{
    "code": 200,
    "msg": "success",
    "data": {
        "name": "保时捷",
        "type": "乘用车",
        "vin": "WP0AB2978PL130045",
        "date": "2023-01",
        "usage": "非营运",
        "model_no": "WP0AB297",
        "engine_no": "",
        "name_info": "Panamera 2023款 Panamera 4S 2.9T",
        "pailiang": "2.9T",
        "market_price": "121.31万起",
        "fuel_type": "汽油",
        "drivemode": "前置四驱",
        "che_id": 162293,
        "door_num": "5",
        "seat_num": "4",
        "cylinder_num": null,
        "gear_type": "湿式双离合变速箱(DCT)",
        "fuelmethod": "直喷",
        "max_power": "324",
        "max_horsepower": "441",
        "enginemodel": "CSZ",
        "environmentalstandards": "国VI"
    },
    "user_info": {
        "zid": "6",
        "rmb": "0.95000",
        "login_time": "2025-02-22 12:43:03"
    }
}


人车etc关系检验，核验指定人员，是否是指定车辆的所有人，核验一致性！
 39

 GET/POST

 JSON

 支持

 ￥1.4/次

 https://www.yuanxiapi.cn/api/

 https://www.yuanxiapi.cn/api/?id=39&key=请在后台配置&name=姓名&chepai=车牌号

开发文档
 
错误码参照
 
示例代码
参数说明

参数	必填	说明
id	是	接口ID
key	是	对接秘钥
name	是	姓名
chepai	是	车牌号
返回参数

名称	说明
status	验证结果（1:一致；2:不一致）
code	200:成功; 计费
user_info	用户信息; zid:用户ID; cost:单价;rmb:余额; login_time:最近登录;
返回示例

{
    "code": 200,
    "msg": "success",
    "data": {
        "status": 1
    },
    "user_info": {
        "zid": "5",
        "rmb": "1.90300",
        "login_time": "2025-02-22 03:18:02"
    }
}


车牌号所在地区查询或者叫车牌号归属地查询,是指通过车牌号查询该车牌号对应的车辆所在地区。用户输入车牌号后,查询该车牌号对应的地区。
 22

 GET/POST

 JSON

 支持

 免费调用

 https://www.yuanxiapi.cn/api/

 https://www.yuanxiapi.cn/api/?id=22&key=请在后台配置&chepai=车牌号码(可只传入前俩位)

开发文档
 
错误码参照
 
示例代码
参数说明

参数	必填	说明
id	是	接口ID
key	是	对接秘钥
chepai	是	车牌号码(可只传入前俩位)
返回参数

名称	说明
location	归属地
code	200:成功;
user_info	用户信息; zid:用户ID; cost:单价;rmb:余额; login_time:最近登录;
返回示例

{
    "code": 200,
    "msg": "success",
    "data": {
        "chepai": "川A",
        "location": "四川省成都市"
    },
    "user_info": {
        "zid": "6",
        "rmb": "994.28792",
        "login_time": "2025-02-24 13:19:48"
    }
}


通过车牌号或vin车架号即可查询车辆过户次数
 29

 GET/POST

 JSON

 支持

 ￥3.5/次

 https://www.yuanxiapi.cn/api/

 https://www.yuanxiapi.cn/api/?id=29&key=请在后台配置&value=车牌号或vin车架号

开发文档
 
错误码参照
 
示例代码
参数说明

参数	必填	说明
id	是	接口ID
key	是	对接秘钥
value	是	车牌号或vin车架号
返回参数

名称	说明
guohu_num	过户次数
code	200:成功; 计费
user_info	用户信息; zid:用户ID; cost:单价;rmb:余额; login_time:最近登录;
返回示例

{
    "code": 200,
    "msg": "success",
    "data": {
        "guohu_num": "0次",
        "value": "川A19A47"
    },
    "user_info": {
        "zid": "6",
        "rmb": "0.95000",
        "login_time": "2025-02-22 12:43:03"
    }
}


个人根据证件号码查询名下车辆数量。 公司根据统一社会信用代码查询名下车辆数量。
数据源于：名下交强险或商业险 车保单总数，精准无误！
Ps：才购买的二手车可能上一任车主购买的保险还未到期！
 30

 GET/POST

 JSON

 支持

 ￥4.5/次

 https://www.yuanxiapi.cn/api/

 https://www.yuanxiapi.cn/api/?id=30&key=请在后台配置&value=身份证号或公司社会信用代码

开发文档
 
错误码参照
 
示例代码
参数说明

参数	必填	说明
id	是	接口ID
key	是	对接秘钥
value	是	身份证号或公司社会信用代码
返回参数

名称	说明
mingxia_num	名下车总数
code	200:成功; 计费
user_info	用户信息; zid:用户ID; cost:单价;rmb:余额; login_time:最近登录;
返回示例

{
    "code": 200,
    "msg": "success",
    "data": {
        "mingxia_num": "1辆"
    },
    "user_info": {
        "zid": "6",
        "rmb": "0.95000",
        "login_time": "2025-02-22 12:43:03"
    }
}


根据证件号码查询名下ETC车辆总数。
 42

 GET/POST

 JSON

 支持

 ￥2.4/次

 https://www.yuanxiapi.cn/api/

 https://www.yuanxiapi.cn/api/?id=42&key=请在后台配置&value=身份证号

开发文档
 
错误码参照
 
示例代码
参数说明

参数	必填	说明
id	是	接口ID
key	是	对接秘钥
value	是	身份证号
name	否	姓名
返回参数

名称	说明
mingxia_num	名下ETC数
list	列表
code	200:成功; 计费
user_info	用户信息; zid:用户ID; cost:单价;rmb:余额; login_time:最近登录;
返回示例

{
    "code": 200,
    "msg": "success",
    "data": {
        "mingxia_num": 2,
        "list": [
            "******",
            "******"
        ]
    },
    "user_info": {
        "zid": "217",
        "rmb": "69.50091",
        "login_time": "2025-04-04 03:06:23"
    }
}



人车关系检验，核验指定人员，是否是指定车辆的所有人，核验一致性！
 31

 GET/POST

 JSON

 支持

 ￥3.4/次

 https://www.yuanxiapi.cn/api/

 https://www.yuanxiapi.cn/api/?id=31&key=请在后台配置&name=姓名&chepai=车牌号或vin车架号

开发文档
 
错误码参照
 
示例代码
参数说明

参数	必填	说明
id	是	接口ID
key	是	对接秘钥
name	是	姓名
chepai	是	车牌号或vin车架号
返回参数

名称	说明
state	1:匹配; 2:不匹配
code	200:成功; 计费
user_info	用户信息; zid:用户ID; cost:单价;rmb:余额; login_time:最近登录;
返回示例

{
    "code": 200,
    "msg": "success",
    "data": {
        "state": 1
    },
    "user_info": {
        "zid": "6",
        "rmb": "0.95000",
        "login_time": "2025-02-22 12:43:03"
    }
}


人车关系检验，核验指定人员，是否是指定车辆的所有人，核验一致性！
 41

 GET/POST

 JSON

 支持

 ￥1.9/次

 https://www.yuanxiapi.cn/api/

 https://www.yuanxiapi.cn/api/?id=41&key=请在后台配置&name=姓名&chepai=车牌号

开发文档
 
错误码参照
 
示例代码
参数说明

参数	必填	说明
id	是	接口ID
key	是	对接秘钥
name	是	姓名
chepai	是	车牌号
返回参数

名称	说明
state	1:匹配；2:不匹配；3:查询成功无结果
code	200:成功; 计费
user_info	用户信息; zid:用户ID; cost:单价;rmb:余额; login_time:最近登录;
返回示例

{
    "code": 200,
    "msg": "success",
    "data": {
        "state": 1
    },
    "user_info": {
        "zid": "6",
        "rmb": "0.95000",
        "login_time": "2025-02-22 12:43:03"
    }
}

根据车牌或vin车架号查询车辆交强险投保日期，查询车辆最新上险时间，初次上险时间。
 34

 GET/POST

 JSON

 支持

 ￥2.3/次

 https://www.yuanxiapi.cn/api/

 https://www.yuanxiapi.cn/api/?id=34&key=请在后台配置&value=车牌号或vin车架号

开发文档
 
错误码参照
 
示例代码
参数说明

参数	必填	说明
id	是	接口ID
key	是	对接秘钥
value	是	车牌号或vin车架号
返回参数

名称	说明
old_date	初次上险时间
new_date	最新上险时间
code	200:成功; 计费
user_info	用户信息; zid:用户ID; cost:单价;rmb:余额; login_time:最近登录;
返回示例

{
    "code": 200,
    "msg": "success",
    "data": {
        "new_date": "2024-12",
        "old_date": "2023-01-16",
        "value": "贵A99A47"
    },
    "user_info": {
        "zid": "6",
        "rmb": "10775.41724",
        "login_time": "2025-03-04 00:03:35"
    }
}
