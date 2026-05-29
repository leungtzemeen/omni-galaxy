package com.omnigalaxy.platform.auth.service.impl;

import com.omnigalaxy.common.captcha.enums.OtpScene;
import com.omnigalaxy.common.captcha.manager.OtpManager;
import com.omnigalaxy.common.core.exception.BizException;
import com.omnigalaxy.platform.auth.api.dto.LoginResponse;
import com.omnigalaxy.platform.auth.dto.OtpLoginRequest;
import com.omnigalaxy.platform.auth.dto.PasswordLoginRequest;
import com.omnigalaxy.platform.auth.service.AuthService;
import com.omnigalaxy.platform.auth.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final OtpManager otpManager;
    private final JwtUtils   jwtUtils;
    // TODO Phase 2: private final UserCredentialMapper userCredentialMapper;
    // TODO Phase 2: private final UserProfileClient userProfileClient;

    @Override
    public LoginResponse loginByOtp(OtpLoginRequest request) {
        String identifier = request.getIdentifier();
        String identityType = request.getIdentityType();

        boolean valid = otpManager.verify(OtpScene.LOGIN, identityType, identifier, request.getOtpCode());
        if (!valid) {
            throw new BizException("验证码错误或已过期，请重新获取");
        }

        // TODO Phase 2: 查 user_credential，若不存在则调 UserProfileClient 创建档案后写入凭证（一键登注）
        Long userId        = 10001L;
        List<String> roles = List.of("ROLE_USER");

        log.info("<<<< [Auth] 用户登录成功 identityType: {} identifier: {} userId: {}",
                 identityType, identifier, userId);
        return new LoginResponse(jwtUtils.generateToken(userId, roles), jwtUtils.getExpireSeconds(), userId);
    }

    @Override
    public LoginResponse loginByPassword(PasswordLoginRequest request) {
        // TODO Phase 2: 查 user_credential（loginType=PASSWORD），验证账号是否存在
        // TODO Phase 2: 用 BCrypt 校验密码，支持重试限制与账号临时锁定
        throw new BizException("密码登录功能待 Phase 2 实现");
    }
}
