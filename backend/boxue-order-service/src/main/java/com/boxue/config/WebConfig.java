package com.boxue.config;

import com.boxue.common.auth.UserIdHeaderInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 订单服务 Web 配置：订单/秒杀接口都需要登录，只放行健康检查。
 * 过渡期设计：6-3 网关统一鉴权后会简化。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final UserIdHeaderInterceptor userIdHeaderInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userIdHeaderInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health");
    }
}