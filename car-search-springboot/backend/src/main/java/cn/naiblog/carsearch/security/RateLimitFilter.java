package cn.naiblog.carsearch.security;

import cn.naiblog.carsearch.api.ApiResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component @Order(Ordered.HIGHEST_PRECEDENCE+20)
public class RateLimitFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;private final ConcurrentHashMap<String,Counter>counters=new ConcurrentHashMap<>();
    public RateLimitFilter(ObjectMapper mapper){this.mapper=mapper;}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{String path=req.getRequestURI();int limit=limit(path,req.getMethod());if(limit==0){chain.doFilter(req,res);return;}long minute=Instant.now().getEpochSecond()/60;String auth=req.getHeader("Authorization");String identity=req.getRemoteAddr()+"|"+(auth==null?"":Hashing.sha256(auth));String key=minute+"|"+pathGroup(path)+"|"+identity;Counter counter=counters.computeIfAbsent(key,k->new Counter(minute));int count=counter.value.incrementAndGet();if(counters.size()>10000)counters.entrySet().removeIf(e->e.getValue().minute<minute-2);res.setHeader("X-RateLimit-Limit",String.valueOf(limit));res.setHeader("X-RateLimit-Remaining",String.valueOf(Math.max(0,limit-count)));if(count>limit){res.setStatus(429);res.setContentType(MediaType.APPLICATION_JSON_VALUE);res.setCharacterEncoding("UTF-8");res.setHeader("Retry-After",String.valueOf(60-Instant.now().getEpochSecond()%60));mapper.writeValue(res.getOutputStream(), ApiResponse.error(429,"请求过于频繁，请稍后重试"));return;}chain.doFilter(req,res);}
    private int limit(String p,String method){if(p.equals("/admin-api/login"))return 10;if(p.equals("/api/v1/auth/login"))return 30;if(p.equals("/api/v1/feedback")&&"POST".equals(method))return 5;if(p.equals("/api/v1/checkout/create"))return 10;if(p.startsWith("/api/v1/checkout/"))return 60;if(p.startsWith("/api/v1/"))return 120;if(p.startsWith("/admin-api/"))return 180;if(p.equals("/wechat/message"))return 600;return 0;}
    private String pathGroup(String p){if(p.startsWith("/api/v1/checkout/"))return"checkout";if(p.startsWith("/admin-api/"))return"admin";return p;}private static final class Counter{final long minute;final AtomicInteger value=new AtomicInteger();Counter(long m){minute=m;}}
}
