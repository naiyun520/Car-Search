package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.*;
import cn.naiblog.carsearch.service.CheckoutService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.regex.Pattern;

@RestController @RequestMapping("/api/v1/checkout")
public class CheckoutController {
    private final CheckoutService checkout;private final ApiSupport api;private final JdbcTemplate jdbc;
    public CheckoutController(CheckoutService checkout,ApiSupport api,JdbcTemplate jdbc){this.checkout=checkout;this.api=api;this.jdbc=jdbc;}
    @PostMapping("/create")public ApiResponse<Map<String,Object>> create(@RequestBody Map<String,Object> body,HttpServletRequest req){api.requireProtocol(req);String code=transport(req,"X-Car-Service-Code",body.get("service_code"),Pattern.compile("^[a-z][a-z0-9_]{2,39}$"),"客户端未提交服务编码，请彻底删除旧版小程序后重新打开");String key=transport(req,"X-Idempotency-Key",body.get("request_key"),Pattern.compile("^[A-Za-z0-9_-]{16,64}$"),"客户端未提交有效请求标识，请重新打开最新版小程序");return ApiResponse.ok(checkout.create(user(api.user(req)),code,key,body));}
    @GetMapping("/status")public ApiResponse<Map<String,Object>>status(@RequestParam(name="order_no",required=false)String no,HttpServletRequest r){return ApiResponse.ok(checkout.status(api.user(r).id(),orderNo(r,no)));}
    @PostMapping("/confirm")public ApiResponse<Map<String,Object>>confirm(@RequestBody(required=false)Map<String,Object>b,HttpServletRequest r){return ApiResponse.ok(checkout.status(api.user(r).id(),orderNo(r,b==null?null:String.valueOf(b.getOrDefault("order_no","")))));}
    @PostMapping("/payment")public ApiResponse<Map<String,Object>>payment(@RequestBody(required=false)Map<String,Object>b,HttpServletRequest r){return ApiResponse.ok(checkout.payment(api.user(r).id(),orderNo(r,b==null?null:String.valueOf(b.getOrDefault("order_no","")))));}
    @PostMapping("/query")public ApiResponse<Map<String,Object>>query(@RequestBody(required=false)Map<String,Object>b,HttpServletRequest r){return ApiResponse.ok(checkout.query(api.user(r).id(),orderNo(r,b==null?null:String.valueOf(b.getOrDefault("order_no","")))));}
    @GetMapping("/recoverable")public ApiResponse<Object>recoverable(HttpServletRequest r){api.requireProtocol(r);return ApiResponse.ok(checkout.recoverable(api.user(r).id()));}
    private String orderNo(HttpServletRequest r,String fallback){api.requireProtocol(r);return transport(r,"X-Car-Order-No",fallback,Pattern.compile("^[A-Za-z0-9_|*@-]{8,32}$"),"客户端未提交有效订单号，请从订单记录重新进入");}
    private String transport(HttpServletRequest r,String h,Object fallback,Pattern p,String msg){String v=r.getHeader(h);if(v==null||v.isBlank())v=fallback==null?"":String.valueOf(fallback);v=v.trim();if(!p.matcher(v).matches())throw new BusinessException(msg);return v;}
    private Map<String,Object>user(cn.naiblog.carsearch.security.CurrentUser u){return jdbc.queryForMap("select * from ci_user where id=?",u.id());}
}
