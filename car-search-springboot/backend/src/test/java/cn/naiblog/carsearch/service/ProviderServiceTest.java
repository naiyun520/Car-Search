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
    private final ProviderService provider = new ProviderService(null, new ObjectMapper());

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
}
