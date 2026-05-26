package com.omnigalaxy.common.security.annotation;

import java.lang.annotation.*;

/**
 * 标记接口或方法需要登录后才能访问。
 *
 * <p>SecurityInterceptor 在 preHandle 阶段检查 {@link com.omnigalaxy.common.core.context.UserContext}
 * 是否存在有效的 LoginUser；若不存在则抛出 401 UNAUTHORIZED。
 *
 * <p>方法级注解优先于类级注解。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresLogin {
}
