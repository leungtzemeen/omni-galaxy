package com.omnigalaxy.platform.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关安全配置。
 *
 * <p>示例（application.yml）：
 * <pre>
 * omni-galaxy:
 *   gateway:
 *     security:
 *       public-paths:
 *         - /auth/login/**
 *         - /auth/register/**
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "omni-galaxy.gateway.security")
public class GatewaySecurityProperties {

    /**
     * JwtVerificationFilter 放行名单：命中以下 Ant 路径的请求不做 JWT 校验。
     * 已知技术债：该名单需随各业务域新增公开接口手动维护。
     */
    private List<String> publicPaths = new ArrayList<>();
}