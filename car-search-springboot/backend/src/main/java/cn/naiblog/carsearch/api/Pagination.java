package cn.naiblog.carsearch.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Pagination {
    private Pagination() {}

    public static Map<String,Object> of(List<?> data, long total, int page, int pageSize) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("per_page", pageSize);
        result.put("current_page", page);
        result.put("last_page", Math.max(1, (long) Math.ceil((double) total / pageSize)));
        result.put("data", data);
        return result;
    }
}
