package com.omnigalaxy.platform.auth.dto;

import com.omnigalaxy.common.core.validation.EitherNotBlank;
import com.omnigalaxy.common.core.validation.EmailFormat;
import com.omnigalaxy.common.core.validation.PhoneFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@EitherNotBlank(fieldNames = {"phone", "email"}, message = "{validation.otp.either.phoneOrEmail}")
@Schema(title = "密码登录请求", description = "手机号或邮箱 + 密码登录，含防爆破保护")
public class PasswordLoginRequest {

    @PhoneFormat
    @Schema(description = "手机号（E.164 格式，如 +86 13800138000）", example = "+86 13800138000")
    private String phone;

    @EmailFormat
    @Schema(description = "邮箱地址", example = "user@example.com")
    private String email;

    @NotBlank(message = "{validation.password.notBlank}")
    @Schema(description = "密码", example = "Pass1234", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Schema(description = "图形验证码挑战ID（仅在风控触发、上一次响应携带 challengeId 时需回传）")
    private String challengeId;

    @Schema(description = "图形验证码答案（仅在风控触发、上一次响应携带 challengeId 时需回传）")
    private String challengeAnswer;

    public String getIdentifier() {
        return phone != null ? phone : email;
    }
}