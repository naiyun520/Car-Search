package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.ApiResponse;
import cn.naiblog.carsearch.config.CarProperties;
import cn.naiblog.carsearch.config.SettingService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@RestController @RequestMapping("/api/v1")
public class PublicController {
    private final JdbcTemplate jdbc; private final ObjectMapper mapper; private final SettingService settings; private final CarProperties properties; private final Path icons;
    public PublicController(JdbcTemplate jdbc, ObjectMapper mapper, SettingService settings, CarProperties properties,@Value("${car.storage-path:./runtime/uploads}")String storage) { this.jdbc=jdbc;this.mapper=mapper;this.settings=settings;this.properties=properties;this.icons=Path.of(storage).toAbsolutePath().normalize().resolve("service-icons"); }

    @GetMapping("/bootstrap")
    public ApiResponse<Map<String,Object>> bootstrap() throws Exception {
        List<Map<String,Object>> rows=jdbc.queryForList("select id,code,name,short_name,description,icon,input_schema,result_schema,status,updated_at from ci_service where status in (0,1) order by sort,id");
        List<Map<String,Object>> services=new ArrayList<>(); StringBuilder version=new StringBuilder();
        for(Map<String,Object> row:rows){ version.append(row.get("id")).append(':').append(row.get("code")).append(':').append(row.get("status")).append(':').append(row.get("updated_at")).append('|');
            Map<String,Object> item=new LinkedHashMap<>(); for(String key:List.of("code","name","short_name","description","icon")) item.put(key,row.get(key));
            item.put("input_schema",mapper.readValue(String.valueOf(row.get("input_schema")),new TypeReference<List<Map<String,Object>>>(){}));
            item.put("result_schema",mapper.readValue(String.valueOf(row.get("result_schema")),new TypeReference<Map<String,Object>>(){})); item.put("status",((Number)row.get("status")).intValue()); services.add(item); }
        List<Map<String,Object>> announcements=jdbc.queryForList("select id,title,content,suppress_hours,updated_at from ci_announcement where status=1 and (start_at is null or start_at<=?) and (end_at is null or end_at>=?) order by id desc limit 1",LocalDateTime.now(),LocalDateTime.now());
        Map<String,Object> publicSettings=new LinkedHashMap<>(); for(String key:List.of("operator_name","privacy_contact","privacy_effective_date","customer_service_phone","customer_service_hours","disclaimer","privacy_retention_days")) publicSettings.put(key,settings.value(key,""));
        String catalog=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(version.toString().getBytes(StandardCharsets.UTF_8)));
        Map<String,Object> result=new LinkedHashMap<>(); result.put("app_version","20260815-query-v1"); result.put("protocol_version",properties.clientProtocol()); result.put("catalog_version",catalog); result.put("services",services); result.put("announcement",announcements.isEmpty()?null:announcements.getFirst()); result.put("settings",publicSettings); return ApiResponse.ok(result);
    }

    @GetMapping("/service-icon/{file}")
    public ResponseEntity<Resource> icon(@PathVariable String file) throws Exception {
        if (!file.matches("[a-f0-9]{32}\\.(png|jpg|webp)")) return ResponseEntity.notFound().build();
        Path target=icons.resolve(file).normalize();
        if(!target.getParent().equals(icons)||!java.nio.file.Files.isRegularFile(target))return ResponseEntity.notFound().build();
        MediaType type=file.endsWith(".png")?MediaType.IMAGE_PNG:file.endsWith(".jpg")?MediaType.IMAGE_JPEG:MediaType.parseMediaType("image/webp");
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic()).header("X-Content-Type-Options","nosniff").body(new UrlResource(target.toUri()));
    }
}
