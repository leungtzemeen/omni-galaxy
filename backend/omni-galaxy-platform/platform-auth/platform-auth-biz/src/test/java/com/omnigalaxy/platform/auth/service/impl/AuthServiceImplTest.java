package com.omnigalaxy.platform.auth.service.impl;

import com.omnigalaxy.common.captcha.dto.CaptchaChallengeResponse;
import com.omnigalaxy.common.captcha.manager.HumanVerificationManager;
import com.omnigalaxy.common.captcha.manager.OtpManager;
import com.omnigalaxy.platform.auth.api.result.AuthResultCodeEnum;
import com.omnigalaxy.platform.auth.component.AccountLifecycleManager;
import com.omnigalaxy.platform.auth.component.LoginRateLimiter;
import com.omnigalaxy.platform.auth.component.TokenBlacklistManager;
import com.omnigalaxy.platform.auth.component.TokenIssuer;
import com.omnigalaxy.platform.auth.domain.UserCredential;
import com.omnigalaxy.platform.auth.dto.PasswordLoginRequest;
import com.omnigalaxy.platform.auth.exception.CaptchaChallengeException;
import com.omnigalaxy.platform.auth.service.UserCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthServiceImpl#loginByPassword} 单元测试：聚焦软挑战激活、验证码连错冷却熔断、
 * 以及 GETDEL 并发竞态下验证码错误三条核心风控分支。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String IDENTIFIER = "13800000000";

    @Mock private OtpManager              otpManager;
    @Mock private TokenIssuer             tokenIssuer;
    @Mock private UserCredentialService   credentialService;
    @Mock private AccountLifecycleManager accountLifecycleManager;
    @Mock private PasswordEncoder         passwordEncoder;
    @Mock private LoginRateLimiter        rateLimiter;
    @Mock private HumanVerificationManager humanVerificationManager;
    @Mock private TokenBlacklistManager   tokenBlacklistManager;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(otpManager, tokenIssuer, credentialService,
                accountLifecycleManager, passwordEncoder, rateLimiter, humanVerificationManager,
                tokenBlacklistManager);
    }

    private PasswordLoginRequest buildRequest(String challengeId, String challengeAnswer) {
        PasswordLoginRequest request = new PasswordLoginRequest();
        request.setPhone(IDENTIFIER);
        request.setPassword("wrong-password");
        request.setChallengeId(challengeId);
        request.setChallengeAnswer(challengeAnswer);
        return request;
    }

    @Test
    void loginByPassword_secondConsecutiveWrongPassword_activatesChallengeAndThrowsCaptchaChallengeException() {
        // 本次请求开始时累计失败数=1（未达阈值），密码比对失败 → recordFailure 后计数=2 → 即时激活软挑战
        when(rateLimiter.needsChallenge(IDENTIFIER)).thenReturn(false, true);

        UserCredential cred = new UserCredential();
        cred.setUserId(1L);
        cred.setCredential("encoded-password");
        cred.setStatus(0);
        when(credentialService.findByIdentity("PASSWORD", IDENTIFIER)).thenReturn(cred);
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        CaptchaChallengeResponse nextChallenge = new CaptchaChallengeResponse("new-challenge-id", "data:image/png;base64,xxx");
        when(humanVerificationManager.generateChallenge()).thenReturn(nextChallenge);

        PasswordLoginRequest request = buildRequest(null, null);

        assertThatThrownBy(() -> authService.loginByPassword(request))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(CaptchaChallengeException.class))
                .satisfies(e -> {
                    assertThat(e.getResultCode()).isEqualTo(AuthResultCodeEnum.PASSWORD_WRONG);
                    assertThat(e.getNextChallenge()).isSameAs(nextChallenge);
                });

        verify(rateLimiter).recordFailure(IDENTIFIER);
        verify(humanVerificationManager, never()).verify(any(), any());
    }

    @Test
    void loginByPassword_captchaWrongThirdConsecutiveTime_triggersOneMinuteCooldown() {
        // 已处于软挑战区间，且本次验证码答案错误恰好是连续第3次 → recordCaptchaFailure 触顶返回 true
        when(rateLimiter.needsChallenge(IDENTIFIER)).thenReturn(true);
        when(humanVerificationManager.verify("challenge-id", "wrong-answer")).thenReturn(false);
        when(rateLimiter.recordCaptchaFailure(IDENTIFIER)).thenReturn(true);

        CaptchaChallengeResponse nextChallenge = new CaptchaChallengeResponse("fresh-challenge-id", "data:image/png;base64,yyy");
        when(humanVerificationManager.generateChallenge()).thenReturn(nextChallenge);

        PasswordLoginRequest request = buildRequest("challenge-id", "wrong-answer");

        assertThatThrownBy(() -> authService.loginByPassword(request))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(CaptchaChallengeException.class))
                .satisfies(e -> {
                    assertThat(e.getResultCode()).isEqualTo(AuthResultCodeEnum.CAPTCHA_FAIL_TOO_MANY);
                    assertThat(e.getNextChallenge()).isSameAs(nextChallenge);
                });

        // 验证码失败不计入密码失败计数，且不会查库比对密码（提前短路）
        verify(rateLimiter, never()).recordFailure(any());
        verify(credentialService, never()).findByIdentity(any(), any());
        verify(rateLimiter, never()).clearCaptchaFailure(any());
    }

    @Test
    void loginByPassword_humanVerifyReturnsFalseFromConcurrentGetAndDelete_throwsCaptchaWrong() {
        // 模拟高并发场景：另一并发请求已先行 GETDEL 消费挑战，本次 verify 拿到 null → 返回 false，
        // 且尚未触发3次冷却 → 仅命中普通 CAPTCHA_WRONG 分支
        when(rateLimiter.needsChallenge(IDENTIFIER)).thenReturn(true);
        when(humanVerificationManager.verify("challenge-id", "any-answer")).thenReturn(false);
        when(rateLimiter.recordCaptchaFailure(IDENTIFIER)).thenReturn(false);

        CaptchaChallengeResponse nextChallenge = new CaptchaChallengeResponse("fresh-challenge-id-2", "data:image/png;base64,zzz");
        when(humanVerificationManager.generateChallenge()).thenReturn(nextChallenge);

        PasswordLoginRequest request = buildRequest("challenge-id", "any-answer");

        assertThatThrownBy(() -> authService.loginByPassword(request))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(CaptchaChallengeException.class))
                .satisfies(e -> {
                    assertThat(e.getResultCode()).isEqualTo(AuthResultCodeEnum.CAPTCHA_WRONG);
                    assertThat(e.getNextChallenge()).isSameAs(nextChallenge);
                });
    }
}