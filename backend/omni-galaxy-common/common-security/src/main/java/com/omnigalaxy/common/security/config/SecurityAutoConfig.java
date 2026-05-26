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
 * <p>仅在 Servlet Web 容器环境下激活（{@code @ConditionalOnWebApplication}），
 * 将 {@link SecurityInterceptor} 注册为全局拦截器，白名单路径通过
 * {@code omni-galaxy.security.whitelist} 属性注入。
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication
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
