-- ====================================================================
-- OmniGalaxy 用户中心库（omni_galaxy_user）
-- 职责：纯用户档案信息，零凭证、零权限
-- 对应微服务：platform-user
-- ====================================================================

CREATE DATABASE IF NOT EXISTS omni_galaxy_user
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE omni_galaxy_user;

-- --------------------------------------------------------------------
-- user_profile 用户档案表
--
-- 设计原则：
--   1. 永不物理删除；账号生命周期由 status + cancelled_at 管控
--   2. deleted 字段（BaseEntity 标准字段）供系统级强制隐藏使用
--      与 status 语义分离：deleted=1 由管理员/风控触发；
--      status=2 是用户主动注销的最终态
--   3. 注销宽限期流程：
--      申请注销 → status=1 + cancelled_at=now()
--      宽限期内反悔 → status=0 + cancelled_at=null（重绑凭证）
--      宽限期到期（定时 Job）→ status=2 + PII 字段匿名化
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS user_profile;
CREATE TABLE user_profile
(
    id           BIGINT       NOT NULL COMMENT '分布式主键（雪花算法 / Bootstrap Seed ID）',
    nickname     VARCHAR(64)  NOT NULL COMMENT '用户昵称',
    avatar       VARCHAR(512)          DEFAULT NULL COMMENT '头像 URL',
    gender       TINYINT      NOT NULL DEFAULT 0 COMMENT '性别：0=未知 1=男 2=女',
    birthday     DATE                  DEFAULT NULL COMMENT '生日',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '账号状态：0=正常 1=注销中(宽限期) 2=已注销 3=封禁',
    cancelled_at DATETIME              DEFAULT NULL COMMENT '注销申请时间（宽限期起点，到期后 Job 执行凭证清理与档案匿名化）',
    create_by    BIGINT                DEFAULT NULL COMMENT '创建人 ID（自注册时等于自身 ID）',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by    BIGINT                DEFAULT NULL COMMENT '最后更新人 ID',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '系统级逻辑删除：0=正常 1=已删除',
    PRIMARY KEY (id),
    INDEX idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT = '用户档案表（永不物理删除，status 管控生命周期）';


-- ====================================================================
-- DML：Bootstrap Seed Data
-- Bootstrap Seed ID = 10001
-- 雪花算法生成的业务 ID 最小值约 1.7×10^18，与种子 ID 无冲突风险
-- ====================================================================
INSERT INTO user_profile (id, nickname, avatar, gender, birthday, status, cancelled_at,
                           create_by, create_time, update_by, update_time, deleted)
VALUES (10001, '超级管理员', NULL, 0, NULL, 0, NULL,
        10001, NOW(), 10001, NOW(), 0);
