package com.omnigalaxy.common.security.annotation;

/** {@link RequiresRoles} 的角色匹配逻辑：OR 表示持有任意一个角色即可，AND 表示必须同时持有全部角色。 */
public enum Logical {
    OR, AND
}
