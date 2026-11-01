package cn.naiblog.carsearch.service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 供应商返回状态字段的统一枚举解析 + 报告体判空。
 *
 * <p>三套字段枚举互不相同，必须按字段名分别判定，不能互相套用：
 * <ul>
 *   <li>{@code query_status}：3=查询成功, 7=查询失败（异步查询进度）</li>
 *   <li>{@code result_status}：1=查询成功, 2=查询中, 3=查询失败（报告结果）</li>
 *   <li>{@code order_status}：0=查询中, 1=查询成功, 2=查询失败（订单状态）</li>
 * </ul>
 * 注意 {@code query_status=3} 表示成功而 {@code result_status=3} 表示失败，两者含义相反
 * （此前回调控制器把 result_status 套用 query_status 枚举，导致查询失败被当成功交付）。
 *
 * <p>兼容约定：字段缺失或枚举值不在已知集合内一律返回 {@link #UNKNOWN}，
 * 由调用方沿用旧语义（按完成处理），保证既有业务状态不被误伤。
 */
public enum QueryStatusResolver {
    SUCCESS, RUNNING, FAILED, UNKNOWN;

    /**
     * 按字段名解析状态值，字段缺失或值不在枚举内返回 {@link #UNKNOWN}。
     */
    public static QueryStatusResolver resolve(String field, Object value) {
        if (value == null) return UNKNOWN;
        String s = value instanceof Number n ? String.valueOf(n.intValue()) : String.valueOf(value).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return UNKNOWN;
        return switch (field) {
            case "query_status" -> switch (s) {
                case "3" -> SUCCESS;
                case "7" -> FAILED;
                default -> UNKNOWN;
            };
            case "result_status" -> switch (s) {
                case "1" -> SUCCESS;
                case "2" -> RUNNING;
                case "3" -> FAILED;
                default -> UNKNOWN;
            };
            case "order_status" -> switch (s) {
                case "0" -> RUNNING;
                case "1" -> SUCCESS;
                case "2" -> FAILED;
                default -> UNKNOWN;
            };
            default -> UNKNOWN;
        };
    }

    /** 供应商状态字段名，按优先级返回首个带值的键；都不存在返回 null。 */
    public static String statusField(Map<String, Object> result) {
        if (result == null) return null;
        for (String key : new String[]{"query_status", "result_status", "order_status"}) {
            if (result.get(key) != null) return key;
        }
        return null;
    }

    /**
     * 解析报告体状态：无状态字段或未知枚举值返回 {@link #UNKNOWN}（调用方按旧语义处理）。
     */
    public static QueryStatusResolver resolve(Map<String, Object> result) {
        String field = statusField(result);
        return field == null ? UNKNOWN : resolve(field, result.get(field));
    }

    /** 身份/状态/传输回显键：不作为报告体内容的判定依据（车架号、车牌号等亦可能是查询入参回显）。 */
    private static final Set<String> ECHO_KEYS = Set.of(
            "query_id", "code_id", "order_id", "pid",
            "result_status", "query_status", "order_status", "status",
            "version", "object", "message", "number", "code", "timestamp",
            "vin", "vin_sub", "car_no", "chepai", "plate_number",
            "engine_no", "engine_number", "brand_id", "brand");

    /**
     * 报告体是否有内容：schema 中除回显键外的任一字段在报告里取到非 null 值即为有内容。
     *
     * <p>刻意宽松的取值判定——空数组 {@code []}、数字 {@code 0}、空串 {@code ""} 都算
     * 「查无记录」的正式答复（如名下车辆数 0、脱保 end_date=""），只有 null/缺失才算空。
     * schema 全部字段都是回显键（纯身份类服务，如车牌查车架号）时不判空，返回 true。
     * 仅当调用方已确认状态码为成功时才应据此判空失败。
     */
    public static boolean reportHasContent(Map<String, Object> schema, Map<String, Object> report) {
        if (schema == null || schema.isEmpty()) return true;
        boolean sawContentKey = false;
        for (String key : schema.keySet()) {
            String top = key.split("\\.", 2)[0].replace("[]", "").trim();
            if (ECHO_KEYS.contains(top)) continue;
            sawContentKey = true;
            if (extract(report, key) != null) return true;
        }
        return !sawContentKey;
    }

    /**
     * 取 schema 键在报告中的值；数组段（如 {@code content.lists[]}）只要非 null 即返回
     * （空数组也算正式答复），缺失返回 null。语法与 normalize 的路径规则一致（支持 " []" 空格写法）。
     */
    private static Object extract(Map<String, Object> report, String schemaKey) {
        Object cur = report;
        for (String seg : schemaKey.replace(" []", "[]").trim().split("\\.")) {
            boolean arr = seg.endsWith("[]");
            String name = arr ? seg.substring(0, seg.length() - 2) : seg;
            if (!(cur instanceof Map<?, ?> m)) return null;
            cur = m.get(name);
            if (cur == null) return null;
            if (arr || cur instanceof Collection<?>) return cur;
        }
        return cur;
    }
}
