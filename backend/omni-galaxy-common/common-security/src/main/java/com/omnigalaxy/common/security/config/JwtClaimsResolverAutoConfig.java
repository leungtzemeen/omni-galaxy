package com.omnigalaxy.common.security.config;

import com.omnigalaxy.common.security.jwt.JwtClaimsResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link JwtClaimsResolver} 自动装配入口。
 *
 * <p>不限定 {@code @ConditionalOnWebApplication} 类型——
 * Servlet 微服务（platform-auth-biz 的 /auth/logout）与响应式网关
 * （platform-gateway 的令牌校验过滤器）都需要复用同一份解析逻辑。
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(JwtSecurityProperties.class)
public class JwtClaimsResolverAutoConfig {

    @Bean
    public JwtClaimsResolver jwtClaimsResolver(JwtSecurityProperties properties) {
        log.info(">>>> [common-security] JwtClaimsResolver 装配完成");
        return new JwtClaimsResolver(properties);
    }
}