package com.omnigalaxy.common.security.config;

import com.omnigalaxy.common.security.interceptor.SecurityInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * common-security 自动装配入口。
 *
 * <p>仅在 Servlet Web 容器环境下激活（显式限定 {@code Type.SERVLET}）。
 * 本类实现了 {@link WebMvcConfigurer}，若放行默认的 {@code Type.ANY}，
 * 在纯 WebFlux 进程（如 platform-gateway）中 classpath 缺失 spring-webmvc，
 * 装配时会因 WebMvcConfigurer 类不可解析而导致 NoClassDefFoundError，进程无法启动。
 *
 * <p>将 {@link SecurityInterceptor} 注册为全局拦截器，白名单路径通过
 * {@code omni-galaxy.security.whitelist} 属性注入。
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityAutoConfig implements WebMvcConfigurer {

    private final SecurityProperties properties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info(">>>> [common-security] 安全拦截器注册完成，白名单路径 content: {}", properties.getWhitelist());
        registry.addInterceptor(new SecurityInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(properties.getWhitelist());
    }
}
