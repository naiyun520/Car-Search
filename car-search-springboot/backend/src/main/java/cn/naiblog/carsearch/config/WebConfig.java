package cn.naiblog.carsearch.config;

import cn.naiblog.carsearch.security.UserAuthInterceptor;
import cn.naiblog.carsearch.security.AdminAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final UserAuthInterceptor userAuth;
    private final AdminAuthInterceptor adminAuth;
    private final String storagePath;

    public WebConfig(UserAuthInterceptor userAuth, AdminAuthInterceptor adminAuth,
                     @Value("${car.storage-path:./runtime/uploads}") String storagePath) {
        this.userAuth = userAuth;
        this.adminAuth = adminAuth;
        this.storagePath = storagePath;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userAuth).addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/bootstrap", "/api/v1/service-icon/**", "/api/v1/auth/login", "/uploads/**");
        registry.addInterceptor(adminAuth).addPathPatterns("/admin-api/**").excludePathPatterns("/admin-api/login");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 静态资源映射：/uploads/** -> 本地文件系统
        String uploadPath = Path.of(storagePath).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath.endsWith("/") ? uploadPath : uploadPath + "/");
    }
}
