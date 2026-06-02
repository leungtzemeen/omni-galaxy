package com.omnigalaxy.platform.user.api.client;

import com.omnigalaxy.common.core.result.Result;
import com.omnigalaxy.platform.user.api.dto.UserProfileCreateDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 用户中心内部 Feign 契约。
 * 仅供 platform-auth 等平台层服务调用，不对网关外暴露。
 */
@FeignClient(name = "platform-user", url = "${platform-user.base-url:}", path = "/inner/user")
public interface UserProfileClient {

    /**
     * 创建用户档案，返回系统分配的 userId（雪花 ID）。
     * platform-auth 拿到此 ID 后写入 user_credential.user_id。
     */
    @PostMapping("/profile")
    Result<Long> createProfile(@RequestBody UserProfileCreateDTO dto);
}
