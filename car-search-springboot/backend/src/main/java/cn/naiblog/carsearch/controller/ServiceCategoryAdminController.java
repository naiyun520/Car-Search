package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.ApiResponse;
import cn.naiblog.carsearch.api.BusinessException;
import cn.naiblog.carsearch.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/admin-api/service-categories")
public class ServiceCategoryAdminController {
    private static final Set<String> ICONS = Set.of("grid", "car", "verified", "shield", "road", "search", "document");
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public ServiceCategoryAdminController(JdbcTemplate jdbc, AuditService audit) { this.jdbc = jdbc; this.audit = audit; }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(jdbc.queryForList("""
                select c.id,c.code,c.name,c.icon,c.sort,c.status,c.created_at,c.updated_at,count(s.id) service_count
                from ci_service_category c left join ci_service s on s.category_id=c.id
                group by c.id,c.code,c.name,c.icon,c.sort,c.status,c.created_at,c.updated_at
                order by c.sort,c.id
                """));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Values value = values(body, false);
        if (scalar("select count(*) from ci_service_category where code=?", value.code) > 0) throw new BusinessException("分类编码已存在");
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("insert into ci_service_category(code,name,icon,sort,status,created_at,updated_at) values(?,?,?,?,?,?,?)",
                value.code, value.name, value.icon, scalar("select coalesce(max(sort),0)+10 from ci_service_category"), value.status, now, now);
        int id = Objects.requireNonNull(jdbc.queryForObject("select last_insert_id()", Integer.class));
        audit.record(request, "service_category.create", "service_category", String.valueOf(id), Map.of("code", value.code));
        return ApiResponse.ok(Map.of("id", id), "服务分类已创建");
    }

    @PostMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable int id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        find(id); Values value = values(body, true);
        jdbc.update("update ci_service_category set name=?,icon=?,status=?,updated_at=? where id=?", value.name, value.icon, value.status, LocalDateTime.now(), id);
        audit.record(request, "service_category.update", "service_category", String.valueOf(id), Map.of("name", value.name));
        return ApiResponse.ok(Map.of("id", id), "服务分类已保存");
    }

    @PostMapping("/sort")
    @Transactional
    public ApiResponse<Object> sort(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        List<Integer> ids = body.get("ids") instanceof List<?> list ? list.stream().map(this::number).filter(v -> v > 0).distinct().toList() : List.of();
        List<Integer> current = jdbc.queryForList("select id from ci_service_category order by id", Integer.class);
        List<Integer> a = new ArrayList<>(ids), b = new ArrayList<>(current); Collections.sort(a); Collections.sort(b);
        if (ids.size() < 2 || !a.equals(b)) throw new BusinessException("分类排序数据不完整，请刷新后重试");
        for (int i = 0; i < ids.size(); i++) jdbc.update("update ci_service_category set sort=?,updated_at=? where id=?", (i + 1) * 10, LocalDateTime.now(), ids.get(i));
        audit.record(request, "service_category.sort", "service_category", "", Map.of("ids", ids));
        return ApiResponse.ok(null, "分类排序已保存");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Object> delete(@PathVariable int id, HttpServletRequest request) {
        find(id);
        if (scalar("select count(*) from ci_service where category_id=?", id) > 0) throw new BusinessException("该分类下仍有接口服务，请先移动服务后再删除");
        jdbc.update("delete from ci_service_category where id=?", id);
        audit.record(request, "service_category.delete", "service_category", String.valueOf(id), Map.of());
        return ApiResponse.ok(null, "服务分类已删除");
    }

    private Values values(Map<String, Object> body, boolean update) {
        String code = text(body.get("code")), name = text(body.get("name")), icon = text(body.getOrDefault("icon", "grid")); int status = number(body.getOrDefault("status", 1));
        if (!update && !code.matches("[a-z][a-z0-9_]{2,39}")) throw new BusinessException("分类编码需为3至40位小写字母、数字或下划线");
        if (name.isBlank() || name.length() > 40) throw new BusinessException("分类名称不能为空且不能超过40字");
        if (!ICONS.contains(icon)) throw new BusinessException("分类图标无效");
        if (status != 0 && status != 1) throw new BusinessException("分类状态无效");
        return new Values(code, name, icon, status);
    }
    private Map<String, Object> find(int id) { List<Map<String, Object>> rows=jdbc.queryForList("select * from ci_service_category where id=?",id);if(rows.isEmpty())throw new BusinessException(404, HttpStatus.NOT_FOUND,"服务分类不存在");return rows.getFirst(); }
    private long scalar(String sql,Object...args){Long value=jdbc.queryForObject(sql,Long.class,args);return value==null?0:value;}
    private int number(Object value){try{return value instanceof Number n?n.intValue():Integer.parseInt(text(value));}catch(Exception ignored){return 0;}}
    private String text(Object value){return value==null?"":String.valueOf(value).trim();}
    private record Values(String code,String name,String icon,int status){}
}
