package org.example.mynote.config;

import org.example.mynote.interceptor.AuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.nio.file.Paths;

/**
 * Web 层配置：跨域、登录拦截、上传目录静态映射、口令编码器。
 *
 * <p>只引入 spring-security-crypto 提供 BCrypt，不启用完整 Security 过滤链，
 * 保持"单机自托管 + JWT"的轻量结构，减少不必要的中间件。</p>
 *
 * @author MyNote
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 前端 Vite 默认端口
     */
    private static final String DEV_FRONTEND_ORIGIN = "http://localhost:5173";

    private static final String DEV_FRONTEND_IPV6_ORIGIN = "http://[::1]:5173";

    private final AuthInterceptor authInterceptor;

    private final AppProperties appProperties;

    public WebConfig(AuthInterceptor authInterceptor, AppProperties appProperties) {
        this.authInterceptor = authInterceptor;
        this.appProperties = appProperties;
    }

    /**
     * 注册登录拦截器。/api/auth/** 为白名单，上传的静态资源不参与鉴权。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/upload/**", "/error");
    }

    /**
     * 把本地上传目录映射为可访问的静态资源，笔记里的图片以相对路径引用。
     *
     * @param registry 资源处理器注册表
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        File dir = Paths.get(appProperties.getUpload().getDir()).toAbsolutePath().normalize().toFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("上传目录创建失败: " + dir.getAbsolutePath());
        }
        registry.addResourceHandler(appProperties.getUpload().getUrlPrefix() + "/**")
                .addResourceLocations("file:" + dir.getAbsolutePath() + File.separator);
    }

    /**
     * 跨域配置。开发环境前端在 5173 端口，直接放行 localhost 与 127.0.0.1 两种写法。
     *
     * @return CORS 过滤器
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("http://localhost:*");
        config.addAllowedOriginPattern("http://127.0.0.1:*");
        config.addAllowedOrigin(DEV_FRONTEND_ORIGIN);
        config.addAllowedOrigin(DEV_FRONTEND_IPV6_ORIGIN);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        config.addExposedHeader("Content-Disposition");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    /**
     * 口令编码器：BCrypt 自带随机盐，同一口令两次散列结果不同。
     *
     * @return 编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
