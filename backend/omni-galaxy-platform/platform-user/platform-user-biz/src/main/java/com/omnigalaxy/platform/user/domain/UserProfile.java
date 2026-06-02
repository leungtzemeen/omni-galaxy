package com.omnigalaxy.platform.user.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omnigalaxy.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户档案实体，对应 omni_galaxy_user.user_profile 表。
 * 永不物理删除，账号生命周期由 status + cancelledAt 管控。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_profile")
public class UserProfile extends BaseEntity {

    private String nickname;

    private String avatar;

    /** 性别：0=未知  1=男  2=女 */
    private Integer gender;

    private LocalDate birthday;

    /** 账号状态：0=正常  1=注销中(宽限期)  2=已注销  3=封禁 */
    private Integer status;

    private LocalDateTime cancelledAt;
}