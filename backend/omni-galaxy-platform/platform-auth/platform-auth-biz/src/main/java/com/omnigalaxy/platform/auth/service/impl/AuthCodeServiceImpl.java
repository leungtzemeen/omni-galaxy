package com.omnigalaxy.platform.auth.service.impl;

import com.omnigalaxy.common.captcha.enums.OtpScene;
import com.omnigalaxy.common.captcha.manager.OtpManager;
import com.omnigalaxy.common.captcha.sender.CaptchaSender;
import com.omnigalaxy.common.core.exception.BizException;
import com.omnigalaxy.platform.auth.api.result.AuthResultCodeEnum;
import com.omnigalaxy.platform.auth.dto.SendCodeRequest;
import com.omnigalaxy.platform.auth.service.AuthCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthCodeServiceImpl implements AuthCodeService {

    private final OtpManager     otpManager;
    private final CaptchaSender captchaSender;

    @Override
    public void sendCode(SendCodeRequest request) {
        OtpScene scene = parseScene(request.getScene());
        String code = otpManager.generateAndStore(scene, request.getIdentityType(), request.getIdentifier());
        captchaSender.send(request.getIdentityType(), request.getIdentifier(), code);
        log.info(">>>> [AuthCode] 验证码已发送 identityType: {} identifier: {}",
                 request.getIdentityType(), request.getIdentifier());
    }

    private OtpScene parseScene(String scene) {
        try {
            return OtpScene.valueOf(scene.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(AuthResultCodeEnum.OTP_SCENE_UNSUPPORTED, scene);
        }
    }
}
