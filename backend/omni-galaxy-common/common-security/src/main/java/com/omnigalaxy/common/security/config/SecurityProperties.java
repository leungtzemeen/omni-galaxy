package com.omnigalaxy.common.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * common-security 可配置属性。
 *
 * <p>示例（application.yml）：
 * <pre>
 * omni-galaxy:
 *   security:
 *     whitelist:
 *       - /actuator/**
 *       - /v3/api-docs/**
 *       - /doc.html
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "omni-galaxy.security")
public class SecurityProperties {

    /**
     * 安全拦截器放行路径列表。
     * /error 为内置兜底白名单，防止 Spring 错误转发时拦截器二次触发。
     */
    private List<String> whitelist = new ArrayList<>(List.of("/error"));
}
