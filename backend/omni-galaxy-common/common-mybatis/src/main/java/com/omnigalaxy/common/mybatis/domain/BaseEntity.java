package com.omnigalaxy.common.mybatis.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 全局物理表公共字段基类。
 * 业务 Entity 继承此类即可零配置获得：雪花ID、审计字段自动填充、逻辑删除。
 *
 * <p>⚠️ 逻辑删除注意：@TableLogic 只对 MyBatis-Plus 框架生成的 SQL 自动追加
 * {@code WHERE deleted = 0}，Mapper.xml 中手写的自定义 SQL 需手动加此条件，
 * 否则会查出已软删除的数据。
 */
@Data
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分布式唯一主键（雪花算法，无需手动赋值） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 创建人（存用户 ID，不存用户名，防止用户改名后审计链断裂） */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 最后更新人 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标志（0=未删除，1=已删除） */
    @TableLogic
    private Integer deleted;
}
