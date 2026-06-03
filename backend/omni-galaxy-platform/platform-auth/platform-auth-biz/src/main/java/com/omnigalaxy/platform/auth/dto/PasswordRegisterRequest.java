package com.omnigalaxy.platform.auth.dto;

import com.omnigalaxy.common.core.validation.EitherNotBlank;
import com.omnigalaxy.common.core.validation.EmailFormat;
import com.omnigalaxy.common.core.validation.PasswordStrength;
import com.omnigalaxy.common.core.validation.PhoneFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@EitherNotBlank(fieldNames = {"phone", "email"}, message = "{validation.otp.either.phoneOrEmail}")
@Schema(title = "账密注册请求", description = "手机号或邮箱 + 密码 + OTP 验证码注册，注册成功后自动登录")
public class PasswordRegisterRequest {

    @PhoneFormat
    @Schema(description = "手机号（E.164 格式，如 +86 13800138000）", example = "+86 13800138000")
    private String phone;

    @EmailFormat
    @Schema(description = "邮箱地址", example = "user@example.com")
    private String email;

    @NotBlank(message = "{validation.password.notBlank}")
    @PasswordStrength
    @Schema(description = "密码（8-20 位，需包含大小写字母和数字）", example = "Pass1234", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotBlank(message = "{validation.otp.code.notBlank}")
    @Pattern(regexp = "^\\d{6}$", message = "{validation.otp.code.pattern}")
    @Schema(description = "验证码（6位数字）", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String otpCode;

    public String getIdentifier() {
        return phone != null ? phone : email;
    }

    public String getIdentityType() {
        return phone != null ? "PHONE" : "EMAIL";
    }
}