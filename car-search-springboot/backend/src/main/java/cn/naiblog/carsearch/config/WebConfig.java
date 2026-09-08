package cn.naiblog.carsearch.config;

import cn.naiblog.carsearch.security.UserAuthInterceptor;
import cn.naiblog.carsearch.security.AdminAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final UserAuthInterceptor userAuth; private final AdminAuthInterceptor adminAuth;
    public WebConfig(UserAuthInterceptor userAuth, AdminAuthInterceptor adminAuth) { this.userAuth = userAuth; this.adminAuth=adminAuth; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userAuth).addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/bootstrap", "/api/v1/service-icon/**", "/api/v1/auth/login");
        registry.addInterceptor(adminAuth).addPathPatterns("/admin-api/**").excludePathPatterns("/admin-api/login");
    }
}
