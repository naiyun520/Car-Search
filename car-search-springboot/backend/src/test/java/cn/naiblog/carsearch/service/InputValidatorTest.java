package cn.naiblog.carsearch.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InputValidatorTest {
    @Test
    void plateOrVinKeepsChinesePlateAndNormalizesCopiedWhitespace() {
        List<Map<String,Object>> schema = List.of(Map.of(
                "key", "value",
                "label", "车牌号或VIN",
                "type", "plate_or_vin",
                "required", true));

        Map<String,Object> result = InputValidator.validate(schema, Map.of("value", "　鄂 ａ89d00　"));

        assertEquals("鄂A89D00", result.get("value"));
    }
}
