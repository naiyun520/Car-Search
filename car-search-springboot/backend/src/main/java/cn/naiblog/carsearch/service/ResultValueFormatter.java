package cn.naiblog.carsearch.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Normalizes documented provider enum values for both new and already stored results. */
public final class ResultValueFormatter {
    private ResultValueFormatter() { }

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai"));

    /** 已知的 Unix 时间戳字段名后缀 */
    private static final List<String> TIMESTAMP_SUFFIXES = List.of(
            "time", "date", "dateline", "timestamp", "updated_at", "created_at"
    );

    /**
     * 格式化单个值：枚举翻译、时间戳转换。
     * 对于 Map/List 嵌套结构不做序列化，原样返回供前端结构化渲染。
     */
    public static Object format(String key, Object value) {
        if (value == null) return null;

        // 时间戳转换：10位秒级数字 → 可读日期（兼容 Number 和 String 类型）
        if (isTimestampField(key)) {
            Long ts = null;
            if (value instanceof Number n) ts = n.longValue();
            else if (value instanceof String s) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty() && !trimmed.contains("-")) {
                    try { ts = Long.parseLong(trimmed); } catch (NumberFormatException ignored) {}
                }
            }
            if (ts != null && ts > 1_000_000_000L && ts < 2_000_000_000L) {
                return DATE_FMT.format(Instant.ofEpochSecond(ts));
            }
            // 0 表示未记录
            if (ts != null && ts == 0L && isTimestampField(key)) {
                return "无记录";
            }
        }

        // 枚举翻译（仅处理简单值）
        String path = key == null ? "" : key;
        String last = path.substring(path.lastIndexOf('.') + 1);
        if (List.of("state", "status").contains(last)) {
            Integer code = null;
            if (value instanceof Number n) code = n.intValue();
            else if (value instanceof String s) try { code = Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
            if (code != null) {
                return switch (code) {
                    case 1 -> "匹配";
                    case 2 -> "不匹配";
                    case 3 -> "查询成功无结果";
                    default -> "未知";
                };
            }
        }

        // result_status 枚举（维保等接口）
        if ("result_status".equals(last)) {
            Integer code = null;
            if (value instanceof Number n) code = n.intValue();
            else if (value instanceof String s) try { code = Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
            if (code != null) {
                return switch (code) {
                    case 1 -> "查询成功";
                    case 2 -> "查询中";
                    case 3 -> "查询失败";
                    default -> code;
                };
            }
        }

        // order_status 枚举（过户等异步接口）
        if ("order_status".equals(last)) {
            Integer code = null;
            if (value instanceof Number n) code = n.intValue();
            else if (value instanceof String s) try { code = Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
            if (code != null) {
                return switch (code) {
                    case 0 -> "查询中";
                    case 1 -> "查询成功";
                    case 2 -> "查询失败";
                    default -> code;
                };
            }
        }

        // change_month 过户年月：201606 → 2016-06（已带分隔符的原样返回，保证幂等）
        if ("change_month".equals(last) && value instanceof String s) {
            String t = s.trim();
            if (t.matches("\\d{6}")) return t.substring(0, 4) + "-" + t.substring(4);
            if (t.matches("\\d{8}")) return t.substring(0, 4) + "-" + t.substring(4, 6) + "-" + t.substring(6);
        }

        // schedule 是否调表（里程 info.schedule / content[].schedule）: 0→正常, 1→调表
        if ("schedule".equals(last)) {
            Integer code = null;
            if (value instanceof Number n) code = n.intValue();
            else if (value instanceof String s) try { code = Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
            if (code != null) return code == 1 ? "调表" : "正常";
        }

        // hasErr 里程核验异常标记: true→异常, false→正常
        if ("hasErr".equals(last)) {
            if (value instanceof Boolean b) return b ? "异常" : "正常";
            if (value instanceof String s) {
                String t = s.trim();
                if ("true".equalsIgnoreCase(t)) return "异常";
                if ("false".equalsIgnoreCase(t)) return "正常";
            }
        }

        // car_is_fire / car_is_water 布尔映射
        if ("car_is_fire".equals(last) || "car_is_water".equals(last)) {
            Integer code = null;
            if (value instanceof Number n) code = n.intValue();
            else if (value instanceof String s) try { code = Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
            if (code != null) return code == 1 ? "是" : "否";
        }

        // kilomete_is_normal
        if ("kilomete_is_normal".equals(last)) {
            Integer code = null;
            if (value instanceof Number n) code = n.intValue();
            else if (value instanceof String s) try { code = Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
            if (code != null) return code == 1 ? "正常" : "异常";
        }

        // is_repair（维修详情中的是否维修过）
        if ("is_repair".equals(last)) {
            Integer code = null;
            if (value instanceof Number n) code = n.intValue();
            else if (value instanceof String s) try { code = Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
            if (code != null) {
                return switch (code) {
                    case 0 -> "无";
                    case 1 -> "有";
                    case 2 -> "未知";
                    default -> code;
                };
            }
        }

        // plate_color 车牌颜色
        if ("plate_color".equals(last)) {
            Integer code = null;
            if (value instanceof Number n) code = n.intValue();
            else if (value instanceof String s) try { code = Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
            if (code != null) {
                return switch (code) {
                    case 0 -> "蓝色";
                    case 1 -> "黄色";
                    case 2 -> "黑色";
                    case 3 -> "白色";
                    case 4 -> "渐变绿色";
                    case 5 -> "黄绿双拼色";
                    case 6 -> "蓝白渐变色";
                    case 7 -> "临时牌照";
                    case 11 -> "绿色";
                    case 12 -> "红色";
                    default -> "未知(" + code + ")";
                };
            }
        }

        // vehicle_type 车辆类型
        if ("vehicle_type".equals(last)) {
            Integer code = null;
            if (value instanceof Number n) code = n.intValue();
            else if (value instanceof String s) try { code = Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
            if (code != null) {
                return switch (code) {
                    case 1 -> "一类客车";
                    case 2 -> "二类客车";
                    case 3 -> "三类客车";
                    case 4 -> "四类客车";
                    case 11 -> "一类货车";
                    case 12 -> "二类货车";
                    case 13 -> "三类货车";
                    default -> "未知(" + code + ")";
                };
            }
        }

        // Map/List 嵌套结构：递归格式化内部字段
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (var entry : m.entrySet()) {
                String childKey = key + "." + entry.getKey();
                result.put(String.valueOf(entry.getKey()), format(childKey, entry.getValue()));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof Map<?, ?> m) {
                    // 对列表中的 Map 递归格式化
                    Map<String, Object> formatted = new LinkedHashMap<>();
                    for (var entry : m.entrySet()) {
                        String childKey = key + "." + entry.getKey();
                        formatted.put(String.valueOf(entry.getKey()), format(childKey, entry.getValue()));
                    }
                    result.add(formatted);
                } else {
                    result.add(format(key + "[" + i + "]", item));
                }
            }
            return result;
        }

        return value;
    }

    /** 判断字段名是否可能是时间戳 */
    private static boolean isTimestampField(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        for (String suffix : TIMESTAMP_SUFFIXES) {
            if (lower.endsWith(suffix) || lower.contains("dateline") || lower.contains("timestamp")) return true;
        }
        return false;
    }

    /** 格式化数组：递归处理每个元素（时间戳、枚举等） */
    public static List<Object> formatList(String key, List<?> list) {
        List<Object> result = new ArrayList<>(list.size());
        for (Object item : list) {
            result.add(format(key, item));
        }
        return result;
    }

    public static List<Map<String, Object>> formatItems(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> source)) continue;
            Map<String, Object> copy = new LinkedHashMap<>();
            source.forEach((key, value) -> copy.put(String.valueOf(key), value));
            String key = String.valueOf(copy.getOrDefault("key", ""));
            if (copy.containsKey("value")) copy.put("value", format(key, copy.get("value")));
            result.add(copy);
        }
        return result;
    }
}