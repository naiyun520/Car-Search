<?php

declare(strict_types=1);

namespace app\service;

class ServiceCatalog
{
    public static function all(): array
    {
        return [
            self::item('plate_basic', 2, '车牌五项信息', '车牌五项', '查询品牌、VIN、车辆种类、初登日期及使用性质', 25.00, 0.45, [['key'=>'chepai','label'=>'车牌号','type'=>'plate','required'=>true]], ['name'=>'品牌名称','vin'=>'VIN车架号','date'=>'初登日期','type'=>'车辆种类','usage'=>'使用性质','engine_no'=>'发动机号','model_no'=>'车辆型号']),
            self::item('vin_decode', 11, 'VIN车辆信息解析', 'VIN解析', '通过17位VIN解析车型配置与动力参数', 25.00, 0.043, [['key'=>'vin','label'=>'VIN车架号','type'=>'vin','required'=>true]], ['vin'=>'VIN车架号','name_info'=>'汽车型号','pailiang'=>'车辆排量','market_price'=>'市场价格','fuel_type'=>'燃油类型','drivemode'=>'驱动方式','door_num'=>'车门数量','seat_num'=>'座位数量','cylinder_num'=>'气缸数量','gear_type'=>'变速箱类型','fuelmethod'=>'供油方式','max_power'=>'最大功率','max_horsepower'=>'最大马力','enginemodel'=>'发动机型号','environmentalstandards'=>'排放标准']),
            self::item('plate_full', 7, '车牌综合信息', '综合车况', '一次查询车辆基础信息与车型配置', 25.00, 0.48, [['key'=>'chepai','label'=>'车牌号','type'=>'plate','required'=>true]], ['name'=>'品牌名称','type'=>'车辆种类','vin'=>'VIN车架号','date'=>'初登日期','usage'=>'使用性质','model_no'=>'车辆型号','engine_no'=>'发动机号','name_info'=>'汽车型号','pailiang'=>'车辆排量','market_price'=>'市场价格','fuel_type'=>'燃油类型','drivemode'=>'驱动方式','door_num'=>'车门数量','seat_num'=>'座位数量','cylinder_num'=>'气缸数量','gear_type'=>'变速箱类型','fuelmethod'=>'供油方式','max_power'=>'最大功率','max_horsepower'=>'最大马力','enginemodel'=>'发动机型号','environmentalstandards'=>'排放标准']),
            self::item('etc_owner_verify', 39, 'ETC人车关系核验', 'ETC核验', '核验指定人员是否为指定车辆ETC所有人', 25.00, 1.40, [['key'=>'name','label'=>'姓名','type'=>'name','required'=>true],['key'=>'chepai','label'=>'车牌号','type'=>'plate','required'=>true]], ['status'=>'核验结果']),
            self::item('plate_location', 22, '车牌归属地查询', '归属地', '查询车牌号对应地区，支持输入车牌前两位', 0.01, 0, [['key'=>'chepai','label'=>'车牌号或前两位','type'=>'plate_prefix','required'=>true]], ['chepai'=>'车牌号','location'=>'归属地']),
            self::item('transfer_count', 29, '车辆过户次数', '过户次数', '通过车牌号或VIN查询车辆过户次数', 25.00, 3.50, [['key'=>'value','label'=>'车牌号或VIN','type'=>'plate_or_vin','required'=>true]], ['value'=>'查询对象','guohu_num'=>'过户次数']),
            self::item('owned_vehicle_count', 30, '名下车辆数量', '名下车辆', '通过证件号码或统一社会信用代码查询名下车辆数', 25.00, 4.50, [['key'=>'value','label'=>'证件号码/统一社会信用代码','type'=>'identity','required'=>true]], ['mingxia_num'=>'名下车总数']),
            self::item('owned_etc_count', 42, '名下ETC车辆', '名下ETC', '通过证件号码查询名下ETC车辆总数', 25.00, 2.40, [['key'=>'value','label'=>'证件号码','type'=>'idcard','required'=>true],['key'=>'name','label'=>'姓名（选填）','type'=>'name','required'=>false]], ['mingxia_num'=>'名下ETC数','list'=>'车辆列表']),
            self::item('owner_verify_precise', 31, '精准人车关系核验', '精准核验', '核验姓名与车牌号或VIN是否匹配', 25.00, 3.40, [['key'=>'name','label'=>'姓名','type'=>'name','required'=>true],['key'=>'chepai','label'=>'车牌号或VIN','type'=>'plate_or_vin','required'=>true]], ['state'=>'核验结果']),
            self::item('owner_verify', 41, '人车关系核验', '人车核验', '核验姓名与车牌号是否匹配', 25.00, 1.90, [['key'=>'name','label'=>'姓名','type'=>'name','required'=>true],['key'=>'chepai','label'=>'车牌号','type'=>'plate','required'=>true]], ['state'=>'核验结果']),
            self::item('insurance_dates', 34, '交强险投保日期', '投保日期', '查询车辆初次及最新交强险投保时间', 25.00, 2.30, [['key'=>'value','label'=>'车牌号或VIN','type'=>'plate_or_vin','required'=>true]], ['value'=>'查询对象','old_date'=>'初次上险时间','new_date'=>'最新上险时间']),
        ];
    }

    private static function item(string $code, int $apiId, string $name, string $shortName, string $description, float $salePrice, float $costPrice, array $inputs, array $results): array
    {
        return compact('code', 'apiId', 'name', 'shortName', 'description', 'salePrice', 'costPrice', 'inputs', 'results');
    }
}
