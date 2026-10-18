package cn.naiblog.carsearch.security;

import cn.naiblog.carsearch.config.SettingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/** Serves the admin SPA only below the configured non-secret path prefix. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AdminPathFilter extends OncePerRequestFilter {
    private static final Set<String> ASSETS=Set.of("index.html","style.css","enhancements.css","app.js","errors.js","settings.js","providers.js","services.js","dashboard.js","echarts.min.js");
    private final SettingService settings;
    public AdminPathFilter(SettingService settings){this.settings=settings;}

    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String method=request.getMethod(),uri=request.getRequestURI();
        if(("GET".equals(method)||"HEAD".equals(method))&&"/".equals(uri)){response.sendError(404);return;}
        if(uri.startsWith("/api/")||uri.startsWith("/admin-api/")||uri.startsWith("/actuator/")||uri.startsWith("/wechat/")){chain.doFilter(request,response);return;}
        String slug=normalized(settings.value("admin_path","admin"));String prefix="/"+slug+"/";
        if(uri.equals("/admin")||uri.equals("/"+slug)){response.sendError(404);return;}
        if(uri.startsWith("/admin/")&&!"admin".equals(slug)){response.sendError(404);return;}
        if(uri.startsWith(prefix)){
            if(!"GET".equals(method)&&!"HEAD".equals(method)){response.sendError(405);return;}
            String asset=uri.substring(prefix.length());if(asset.isBlank())asset="index.html";
            if(!ASSETS.contains(asset)){response.sendError(404);return;}
            response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");
            request.getRequestDispatcher("/admin/"+asset).forward(request,response);return;
        }
        chain.doFilter(request,response);
    }
    private String normalized(String value){String slug=value==null?"":value.trim().toLowerCase();return slug.matches("[a-z][a-z0-9-]{2,31}")?slug:"admin";}
}
