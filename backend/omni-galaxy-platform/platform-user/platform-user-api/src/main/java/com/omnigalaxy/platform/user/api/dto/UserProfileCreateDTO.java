package com.omnigalaxy.platform.user.api.dto;

import lombok.Data;

/**
 * 创建用户档案请求 DTO（内部 Feign 契约，不对外暴露 Swagger）。
 * nickname 为空时，platform-user 服务自动生成默认昵称。
 */
@Data
public class UserProfileCreateDTO {

    /** 用户昵称，可为空，服务端自动生成兜底值 */
    private String nickname;

    /**
     * 不透明幂等键（SHA-256 of "identityType:identifier"，十六进制字符串）。
     * platform-user 侧以此为 key 做 10 分钟 Redis 级幂等拦截，
     * 防止 Feign 超时二义性产生孤儿档案。
     * 调用方只需传入 Hash，platform-user 无法反推出原始身份标识。
     */
    private String idempotencyKey;
}
