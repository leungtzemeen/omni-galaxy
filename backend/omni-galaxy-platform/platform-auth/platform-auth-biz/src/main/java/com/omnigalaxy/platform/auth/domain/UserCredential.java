package com.omnigalaxy.platform.auth.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户凭证实体。
 * 对应 omni_galaxy_auth.user_credential 表。
 *
 * <p>⚠️ 该表无 deleted 字段，不做软删，故不继承 BaseEntity。
 * 物理删除由定时 Job 在注销宽限期结束后执行。
 */
@Data
@TableName("user_credential")
public class UserCredential {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 凭证类型：PHONE / EMAIL / PASSWORD / WECHAT 等 */
    private String identityType;

    /** 登录标识：手机号(E.164) / 邮箱 / 用户名 / 社交 openid */
    private String identifier;

    /** BCrypt 密文；OTP/社交登录为 NULL */
    private String credential;

    /** 凭证状态：0=正常  1=禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}