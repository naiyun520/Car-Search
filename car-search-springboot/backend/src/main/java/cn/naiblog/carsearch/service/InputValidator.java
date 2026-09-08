package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.api.BusinessException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class InputValidator {
    private InputValidator() {}
    private static final Map<String,Pattern> PATTERNS = Map.of(
            "vin", Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$"),
            "plate", Pattern.compile("^[\\p{IsHan}][A-Z][A-Z0-9]{5,6}$"),
            "plate_prefix", Pattern.compile("^[\\p{IsHan}][A-Z](?:[A-Z0-9]{5,6})?$"),
            "plate_or_vin", Pattern.compile("^(?:[A-HJ-NPR-Z0-9]{17}|[\\p{IsHan}][A-Z][A-Z0-9]{5,6})$"),
            "idcard", Pattern.compile("^\\d{17}[0-9X]$", Pattern.CASE_INSENSITIVE),
            "identity", Pattern.compile("^(?:\\d{17}[0-9X]|[0-9A-Z]{18})$", Pattern.CASE_INSENSITIVE));

    public static Map<String,Object> validate(List<Map<String,Object>> schema, Map<String,Object> input) {
        Map<String,Object> clean = new LinkedHashMap<>();
        for (Map<String,Object> field : schema) {
            String key = String.valueOf(field.get("key")); String label = String.valueOf(field.get("label"));
            String type = String.valueOf(field.getOrDefault("type", "text"));
            String value = String.valueOf(input.getOrDefault(key, "")).trim();
            if (Boolean.TRUE.equals(field.get("required")) && value.isBlank()) throw new BusinessException(label + "不能为空");
            if (value.isBlank()) continue;
            if (List.of("plate","plate_prefix","plate_or_vin","vin").contains(type)) value = value.replace(" ", "").toUpperCase();
            int length = value.codePointCount(0, value.length());
            boolean valid = PATTERNS.containsKey(type) ? PATTERNS.get(type).matcher(value).matches()
                    : "name".equals(type) ? length >= 2 && length <= 30 : length <= 100;
            if (!valid) throw new BusinessException(label + "格式不正确");
            clean.put(key, value);
        }
        return clean;
    }
}
