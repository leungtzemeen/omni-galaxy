package com.omnigalaxy.platform.user.controller;

import com.omnigalaxy.common.core.result.Result;
import com.omnigalaxy.platform.user.api.dto.UserProfileCreateDTO;
import com.omnigalaxy.platform.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户中心内部接口，仅供平台层服务通过 Feign 调用，不对网关外暴露。
 */
@RestController
@RequestMapping("/inner/user")
@RequiredArgsConstructor
public class UserProfileInnerController {

    private final UserProfileService profileService;

    @PostMapping("/profile")
    public Result<Long> createProfile(@RequestBody UserProfileCreateDTO dto) {
        return Result.success(profileService.createProfile(dto.getNickname(), dto.getIdempotencyKey()));
    }
}