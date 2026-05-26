package com.omnigalaxy.common.security.annotation;

import java.lang.annotation.*;

/**
 * 标记接口或方法需要持有指定角色才能访问。隐含 {@link RequiresLogin} 语义。
 *
 * <p>角色数据由网关通过 X-User-Roles Header（逗号分隔）透传，SecurityInterceptor 负责解析并绑定至上下文。
 *
 * <p>示例：
 * <pre>{@code
 * @RequiresRoles("admin")                              // 持有 admin 角色即可
 * @RequiresRoles({"admin", "super_admin"})             // 持有 admin 或 super_admin 其中之一（默认 OR）
 * @RequiresRoles(value = {"admin", "manager"}, logical = Logical.AND) // 必须同时持有两个角色
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresRoles {

    String[] value();

    Logical logical() default Logical.OR;
}
