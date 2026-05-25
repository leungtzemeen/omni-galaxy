package com.omnigalaxy.common.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 全局配置。
 * 注入分页拦截器，并预留多租户与数据权限扩展插槽。
 *
 * <p>⚠️ 拦截器顺序敏感：多租户 > 数据权限 > 分页，顺序错误会导致 SQL 拼接逻辑异常。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 【预留插槽 1 - 多租户拦截器】启用时取消注释，必须置于分页插件之前
        // interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler));

        // 【预留插槽 2 - 数据权限拦截器】启用时取消注释，需自行实现 DataPermissionHandler
        // interceptor.addInnerInterceptor(new DataPermissionInterceptor(dataPermissionHandler));

        // 分页插件（显式指定 MySQL，防止全量数据穿透查询）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        return interceptor;
    }
}
