package com.omnigalaxy.platform.auth.dto;

import com.omnigalaxy.common.core.validation.EitherNotBlank;
import com.omnigalaxy.common.core.validation.EmailFormat;
import com.omnigalaxy.common.core.validation.PhoneFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@EitherNotBlank(fieldNames = {"phone", "email"}, message = "phone and email: exactly one must be provided")
@Schema(title = "OTP 登录请求", description = "PHONE 或 EMAIL 二选一，配合 OTP 验证码")
public class OtpLoginRequest {

    @PhoneFormat
    @Schema(description = "手机号（E.164 格式，如 +86 13800138000）", example = "+86 13800138000")
    private String phone;

    @EmailFormat
    @Schema(description = "邮箱地址", example = "user@example.com")
    private String email;

    @NotBlank(message = "verification code cannot be blank")
    @Pattern(regexp = "^\\d{6}$", message = "verification code must be exactly 6 digits")
    @Schema(description = "验证码（6位数字）", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String otpCode;

    public String getIdentifier() {
        return phone != null ? phone : email;
    }

    public String getIdentityType() {
        return phone != null ? "PHONE" : "EMAIL";
    }
}
