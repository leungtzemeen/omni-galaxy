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
@Schema(title = "修改密码请求",
        description = "用 OTP 验证码证明身份归属，再设置新密码。适用于已设置密码的账号；" +
                      "未设置密码的 OTP 账号请通过账密注册流程绑定密码。")
public class ChangePasswordRequest {

    @PhoneFormat
    @Schema(description = "手机号（E.164 格式，如 +86 13800138000）", example = "+86 13800138000")
    private String phone;

    @EmailFormat
    @Schema(description = "邮箱地址", example = "user@example.com")
    private String email;

    @NotBlank(message = "{validation.otp.code.notBlank}")
    @Pattern(regexp = "^\\d{6}$", message = "{validation.otp.code.pattern}")
    @Schema(description = "发送至手机/邮箱的 6 位 OTP 验证码（场景：RESET_PWD）",
            example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String otpCode;

    @NotBlank(message = "{validation.password.notBlank}")
    @PasswordStrength
    @Schema(description = "新密码（8-20 位，需包含大小写字母和数字）",
            example = "NewPass1234", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;

    public String getIdentifier() {
        return phone != null ? phone : email;
    }

    public String getIdentityType() {
        return phone != null ? "PHONE" : "EMAIL";
    }
}
