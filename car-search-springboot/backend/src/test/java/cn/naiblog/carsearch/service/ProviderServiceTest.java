package cn.naiblog.carsearch.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProviderServiceTest {
    private final ProviderService provider = new ProviderService(null, new ObjectMapper(), null, "");

    @Test
    void getQueryEncodesChinesePlateExactlyOnce() throws Exception {
        URI result = provider.withQuery(
                new URI("https://www.yuanxiapi.cn/api/?id=29&key=test-key"),
                Map.of("value", "鄂A89D00"));

        assertEquals("id=29&key=test-key&value=%E9%84%82A89D00", result.getRawQuery());
        assertFalse(result.getRawQuery().contains("%25"));
        assertEquals("id=29&key=test-key&value=鄂A89D00",
                URLDecoder.decode(result.getRawQuery(), StandardCharsets.UTF_8));
    }

    @Test
    void postEndpointRemovesBodyKeysWithoutDoubleEncodingRemainingQuery() throws Exception {
        URI result = provider.withoutQueryKeys(
                new URI("https://example.com/api/?id=29&label=%E8%BF%87%E6%88%B7&value=old"),
                Set.of("value"));

        assertEquals("id=29&label=%E8%BF%87%E6%88%B7", result.getRawQuery());
        assertFalse(result.getRawQuery().contains("%25"));
    }

    @Test
    void verificationStateAcceptsNumericAndStringCodes() throws Exception {
        Map<String,Object> service=Map.of("result_schema","{\"state\":\"核验结果\"}");

        assertEquals("匹配",provider.normalize(service,Map.of("state",1)).getFirst().get("value"));
        assertEquals("不匹配",provider.normalize(service,Map.of("state","2")).getFirst().get("value"));
        assertEquals("查询成功无结果",provider.normalize(service,Map.of("state"," 3 ")).getFirst().get("value"));
        assertEquals("不匹配",ResultValueFormatter.formatItems(java.util.List.of(Map.of("key","state","label","核验结果","value","2"))).getFirst().get("value"));
    }

    @Test
    void arraySubFieldMappingBuildsColumnsAndRows() throws Exception {
        String schema="{\"order_status\":\"订单状态\",\"data[]\":\"过户记录\","
                +"\"data[].change_month\":\"过户年月\",\"data[].old_plate\":\"过户前车牌\",\"data[].new_plate\":\"过户后车牌\"}";
        Map<String,Object> service=Map.of("result_schema",schema);
        Map<String,Object> data=Map.of("order_status",1,"data",java.util.List.of(
                Map.of("old_plate","浙J720A7","new_plate","浙B657VT","change_month","201606","city_before",""),
                Map.of("old_plate","浙B657VT","new_plate","浙K808Z0","change_month","201907","city_before","宁波")));

        java.util.List<Map<String,Object>> out=provider.normalize(service,data);

        // 标量字段按原逻辑输出，order_status 翻译枚举
        assertEquals("查询成功",out.stream().filter(m->"order_status".equals(m.get("key"))).findFirst().orElseThrow().get("value"));
        // 数组分组：columns 按配置顺序，行内仅保留配置字段
        Map<String,Object> group=out.stream().filter(m->"data[]".equals(m.get("key"))).findFirst().orElseThrow();
        assertEquals("array",group.get("display"));
        @SuppressWarnings("unchecked")
        java.util.List<Map<String,Object>> columns=(java.util.List<Map<String,Object>>)group.get("columns");
        assertEquals(java.util.List.of("change_month","old_plate","new_plate"),columns.stream().map(c->c.get("key")).toList());
        assertEquals(java.util.List.of("过户年月","过户前车牌","过户后车牌"),columns.stream().map(c->c.get("label")).toList());
        @SuppressWarnings("unchecked")
        java.util.List<Map<String,Object>> rows=(java.util.List<Map<String,Object>>)group.get("value");
        assertEquals(2,rows.size());
        assertEquals(java.util.Set.of("change_month","old_plate","new_plate"),rows.getFirst().keySet());
        // change_month 格式化为年-月，未配置的 city_before 不出现
        assertEquals("2016-06",rows.getFirst().get("change_month"));
        assertEquals("浙K808Z0",rows.get(1).get("new_plate"));
    }

    @Test
    void arrayWithoutSubFieldsKeepsLegacyRawOutput() throws Exception {
        Map<String,Object> service=Map.of("result_schema","{\"data[]\":\"过户记录\"}");
        Map<String,Object> data=Map.of("data",java.util.List.of(Map.of("old_plate","浙J720A7","change_month","201606")));

        Map<String,Object> group=provider.normalize(service,data).getFirst();
        assertEquals("array",group.get("display"));
        assertFalse(group.containsKey("columns"));
        @SuppressWarnings("unchecked")
        java.util.List<Map<String,Object>> rows=(java.util.List<Map<String,Object>>)group.get("value");
        assertEquals("浙J720A7",rows.getFirst().get("old_plate"));
    }

    @Test
    void scheduleAndHasErrTranslateToReadableStatus() {
        assertEquals("正常", ResultValueFormatter.format("info.schedule", 0));
        assertEquals("调表", ResultValueFormatter.format("content[].schedule", 1));
        assertEquals("正常", ResultValueFormatter.format("hasErr", false));
        assertEquals("异常", ResultValueFormatter.format("hasErr", true));
        // 幂等：已翻译的文本原样返回
        assertEquals("正常", ResultValueFormatter.format("schedule", "正常"));
        assertEquals("异常", ResultValueFormatter.format("hasErr", "异常"));
    }

    @Test
    void subFieldKeepsFullArrayAndDottedNestedValues() throws Exception {
        // 保险理赔：出险项列表下嵌套维修列表/数量（maintain_project 是 list 的兄弟节点，需用点路径直达）
        String schema="{\"data[]\":\"出险项\",\"data[].name\":\"名称\",\"data[].items\":\"明细\","
                +"\"data[].nested.count\":\"数量合计\",\"data[].nested.list\":\"维修列表\"}";
        Map<String,Object>service=Map.of("result_schema",schema);
        Map<String,Object>data=Map.of("data",java.util.List.of(
                Map.of("name","A","items",java.util.List.of(Map.of("t","x"),Map.of("t","y")),
                        "nested",Map.of("count",3,"list",java.util.List.of(
                                Map.of("text","前保险杠","num",1),Map.of("text","前车门","num",2))))));

        Map<String,Object>group=provider.normalize(service,data).stream()
                .filter(m->"data[]".equals(m.get("key"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        java.util.List<Map<String,Object>>rows=(java.util.List<Map<String,Object>>)group.get("value");
        // 子字段指向数组时保留整组，不再截断为第一个元素
        assertEquals(2,((java.util.List<?>)rows.getFirst().get("items")).size());
        assertEquals(3,rows.getFirst().get("nested.count"));
        assertEquals(2,((java.util.List<?>)rows.getFirst().get("nested.list")).size());
    }

    @Test
    void subFieldWithoutGroupHeaderStillProducesGroupItem() throws Exception {
        Map<String,Object> service=Map.of("result_schema","{\"data[].old_plate\":\"过户前车牌\"}");
        Map<String,Object> data=Map.of("data",java.util.List.of(Map.of("old_plate","浙J720A7")));

        Map<String,Object> group=provider.normalize(service,data).getFirst();
        assertEquals("data[]",group.get("key"));
        @SuppressWarnings("unchecked")
        java.util.List<Map<String,Object>> rows=(java.util.List<Map<String,Object>>)group.get("value");
        assertEquals("浙J720A7",rows.getFirst().get("old_plate"));
    }

    @Test
    void spaceBeforeArrayBracketStillResolvesGroup() throws Exception {
        // 旧配置曾把数组键写成 "tab []"（方括号前带空格），路径尾空格查不到 → 整组暂无数据
        String schema="{\"tab []\":\"维保明细统计\",\"tab [].type_name\":\"类型\","
                +"\"content [].material\":\"材料\",\"structure_repair_detail.have_detail[]\":\"结构部件\","
                +"\"structure_repair_detail.have_detail[].name\":\"部件\"}";
        Map<String,Object>service=Map.of("result_schema",schema);
        Map<String,Object>data=Map.of(
                "tab",java.util.List.of(Map.of("type_name","全部","num",12)),
                "content",java.util.List.of(Map.of("material",java.util.List.of(
                        Map.of("text","召回配件包","num",2),Map.of("text","原厂配件","num",1)))),
                "structure_repair_detail",Map.of("have_detail",java.util.List.of(
                        Map.of("name","柱体"),Map.of("name","梁体"))));

        java.util.List<Map<String,Object>>out=provider.normalize(service,data);

        Map<String,Object>tab=out.stream().filter(m->"tab[]".equals(m.get("key"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        java.util.List<Map<String,Object>>tabRows=(java.util.List<Map<String,Object>>)tab.get("value");
        assertEquals("全部",tabRows.getFirst().get("type_name"));

        Map<String,Object>content=out.stream().filter(m->"content[]".equals(m.get("key"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        java.util.List<Map<String,Object>>contentRows=(java.util.List<Map<String,Object>>)content.get("value");
        assertEquals(2,((java.util.List<?>)contentRows.getFirst().get("material")).size());

        Map<String,Object>parts=out.stream().filter(m->"structure_repair_detail.have_detail[]".equals(m.get("key"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        java.util.List<Map<String,Object>>partRows=(java.util.List<Map<String,Object>>)parts.get("value");
        assertEquals(2,partRows.size());
        assertEquals("柱体",partRows.getFirst().get("name"));
    }
}
