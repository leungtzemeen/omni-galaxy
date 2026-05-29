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
}
