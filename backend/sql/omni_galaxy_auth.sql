-- ====================================================================
-- OmniGalaxy 认证与权限统一库（omni_galaxy_auth）
-- 职责：SSO 凭证 + 平台级动态 RBAC
--
-- 架构说明：
--   platform-auth（认证微服务）与 platform-permission（权限微服务）
--   共享此物理库，各自独立配置连接池，物理共库、逻辑隔离连接。
--
-- 菜单数据范围：
--   本文件仅初始化平台级系统管理菜单。
--   业务域菜单（mall-permission 等）由各域服务的初始化脚本负责。
-- ====================================================================

CREATE DATABASE IF NOT EXISTS omni_galaxy_auth
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE omni_galaxy_auth;

-- --------------------------------------------------------------------
-- user_credential 用户凭证表
--
-- 设计原则：
--   1. 无 deleted 字段，不做软删
--   2. 注销宽限期内：status=1（禁用），凭证保留供账号恢复使用
--      同时阻止此 identifier 被新用户抢注（宽限期保护）
--   3. 宽限期结束（定时 Job）：物理 DELETE，identifier 彻底释放
--      支持运营商号码回收后新用户重新注册同一手机号
--   4. 手机号存储严格遵循 E.164 国际规范（如 +8613800138000）
--
-- identity_type 枚举（VARCHAR 便于无感扩容，无需 ALTER TABLE）：
--   PHONE   手机号（E.164 格式）
--   EMAIL   邮箱
--   PASSWORD 手机号/邮箱 + 密码（identifier 存手机号(E.164)或邮箱地址）
--   WECHAT  微信
--   ALIPAY  支付宝
--   APPLE   Apple ID（出海必备）
--   GOOGLE  Google（海外主流）
--   GITHUB  GitHub（开发者社区）
--   FEISHU  飞书（企业内部）
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS user_credential;
CREATE TABLE user_credential
(
    id            BIGINT       NOT NULL COMMENT '分布式主键',
    user_id       BIGINT       NOT NULL COMMENT '关联 omni_galaxy_user.user_profile.id',
    identity_type VARCHAR(32)  NOT NULL COMMENT '凭证类型：PHONE/EMAIL/PASSWORD/WECHAT/ALIPAY/APPLE/GOOGLE/GITHUB/FEISHU',
    identifier    VARCHAR(256) NOT NULL COMMENT '登录标识：手机号(E.164)/邮箱/用户名/社交 openid',
    credential    VARCHAR(512)          DEFAULT NULL COMMENT 'BCrypt 密文；社交登录（OAuth2）为 NULL',
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '凭证状态：0=正常 1=禁用（注销宽限期 / 管理员冻结）',
    create_by     BIGINT                DEFAULT NULL COMMENT '创建人 ID',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '凭证创建时间',
    update_by     BIGINT                DEFAULT NULL COMMENT '最后更新人 ID',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间（如密码修改）',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_identity_identifier (identity_type, identifier),
    INDEX idx_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT = '用户凭证表（多渠道登录，E.164 国际规范，无软删）';


-- --------------------------------------------------------------------
-- role 角色表
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS role;
CREATE TABLE role
(
    id          BIGINT       NOT NULL COMMENT '分布式主键',
    name        VARCHAR(64)  NOT NULL COMMENT '角色显示名称（如：超级管理员）',
    code        VARCHAR(64)  NOT NULL COMMENT '角色机器码（如：ROLE_SUPER_ADMIN），供 @PreAuthorize 注解与网关鉴权使用',
    description VARCHAR(256)          DEFAULT NULL COMMENT '角色描述',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0=正常 1=禁用',
    create_by   BIGINT                DEFAULT NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   BIGINT                DEFAULT NULL,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_code (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT = '角色表';


-- --------------------------------------------------------------------
-- menu 菜单权限表（细粒度三级：目录 / 菜单 / 按钮）
--
-- type 值域：
--   0 = 目录：侧边栏分组容器，无路由，path/component/permission 为 NULL
--   1 = 菜单：对应一个前端页面，有 path 和 component
--   2 = 按钮：细粒度操作权限，visible=0（不渲染到侧边栏），
--             permission 为后端鉴权码（如 system:user:delete）
--
-- visible 说明：
--   0 = 隐藏（不渲染到侧边栏，但权限码仍有效，用于纯 API 级控制）
--   1 = 显示
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS menu;
CREATE TABLE menu
(
    id          BIGINT       NOT NULL COMMENT '分布式主键',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父节点 ID；顶级目录为 0',
    name        VARCHAR(64)  NOT NULL COMMENT '节点显示名称',
    type        TINYINT      NOT NULL COMMENT '节点类型：0=目录 1=菜单 2=按钮',
    path        VARCHAR(256)          DEFAULT NULL COMMENT '前端路由路径（按钮类型为 NULL）',
    component   VARCHAR(256)          DEFAULT NULL COMMENT 'Vue 组件路径（如 system/user/index；目录/按钮为 NULL）',
    permission  VARCHAR(128)          DEFAULT NULL COMMENT '后端权限码（如 system:user:list；目录类型为 NULL）',
    icon        VARCHAR(128)          DEFAULT NULL COMMENT '图标标识（目录/菜单级使用）',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '同级节点排序（升序）',
    visible     TINYINT      NOT NULL DEFAULT 1 COMMENT '侧边栏渲染：0=隐藏 1=显示',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0=正常 1=禁用',
    create_by   BIGINT                DEFAULT NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   BIGINT                DEFAULT NULL,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
    PRIMARY KEY (id),
    INDEX idx_parent_id (parent_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT = '菜单权限表（目录/菜单/按钮三级细粒度，对接 Vue3 动态路由）';


-- --------------------------------------------------------------------
-- user_role 用户角色关系表（纯关系表，物理删除）
--
-- 设计原则：
--   关系表只记录"绑定关系"，无实体生命周期，不做软删。
--   解绑直接 DELETE，彻底规避 UNIQUE INDEX 与软删的冲突陷阱。
--   仅保留 create_by + create_time 供审计追溯绑定操作。
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS user_role;
CREATE TABLE user_role
(
    id          BIGINT   NOT NULL COMMENT '分布式主键',
    user_id     BIGINT   NOT NULL COMMENT '用户 ID',
    role_id     BIGINT   NOT NULL COMMENT '角色 ID',
    create_by   BIGINT            DEFAULT NULL COMMENT '绑定操作人 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_user_role (user_id, role_id),
    INDEX idx_role_id (role_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT = '用户角色关系表（物理删除，无软删）';


-- --------------------------------------------------------------------
-- role_menu 角色菜单关系表（纯关系表，物理删除）
-- --------------------------------------------------------------------
DROP TABLE IF EXISTS role_menu;
CREATE TABLE role_menu
(
    id          BIGINT   NOT NULL COMMENT '分布式主键',
    role_id     BIGINT   NOT NULL COMMENT '角色 ID',
    menu_id     BIGINT   NOT NULL COMMENT '菜单 ID',
    create_by   BIGINT            DEFAULT NULL COMMENT '绑定操作人 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_role_menu (role_id, menu_id),
    INDEX idx_menu_id (menu_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT = '角色菜单关系表（物理删除，无软删）';


-- ====================================================================
-- DML：Bootstrap Seed Data（全库数据穿透初始化）
--
-- Bootstrap Seed ID 规则：
--   user_profile.id  = 10001（与 omni_galaxy_user 库保持一致）
--   其余实体（credential / role / menu）使用小整数种子 ID（从 1 起）
--   雪花算法生成的最小业务 ID 约 1.7×10^18，种子 ID 永不冲突
-- ====================================================================

-- --------------------------------------------------------------------
-- 凭证：超管 PASSWORD 凭证
--
-- ⚠️  密码明文：Admin@2024
-- ⚠️  以下为示例 BCrypt 密文，部署前必须用以下代码重新生成后替换：
--     new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("Admin@2024")
-- ⚠️  生产环境上线前必须修改初始密码
-- --------------------------------------------------------------------
INSERT INTO user_credential (id, user_id, identity_type, identifier, credential, status,
                              create_by, create_time, update_by, update_time)
VALUES (1, 10001, 'PASSWORD', 'admin',
        '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2',
        0, 10001, NOW(), 10001, NOW());


-- --------------------------------------------------------------------
-- 角色：平台预置基础角色
-- --------------------------------------------------------------------
INSERT INTO role (id, name, code, description, status, create_by, create_time, update_by, update_time, deleted)
VALUES (1, '超级管理员', 'ROLE_SUPER_ADMIN', '平台最高权限角色，不可删除', 0, 10001, NOW(), 10001, NOW(), 0),
       (2, '普通用户',   'ROLE_USER',        '已登录注册用户的基础角色',   0, 10001, NOW(), 10001, NOW(), 0);


-- --------------------------------------------------------------------
-- 菜单：平台管理后台基础菜单树（系统管理模块）
-- 按钮节点 visible=0，不渲染到侧边栏，仅用于后端权限码鉴权
-- --------------------------------------------------------------------
INSERT INTO menu (id, parent_id, name, type, path, component, permission, icon, sort, visible, status,
                  create_by, create_time, update_by, update_time, deleted)
VALUES
-- ── 一级目录 ────────────────────────────────────────────────────────
(1,  0, '系统管理', 0, '/system',      NULL,                    NULL,                      'setting',    1, 1, 0, 10001, NOW(), 10001, NOW(), 0),

-- ── 二级菜单 ────────────────────────────────────────────────────────
(2,  1, '用户管理', 1, '/system/user', 'system/user/index',     'system:user:list',        'user',       1, 1, 0, 10001, NOW(), 10001, NOW(), 0),
(3,  1, '角色管理', 1, '/system/role', 'system/role/index',     'system:role:list',        'peoples',    2, 1, 0, 10001, NOW(), 10001, NOW(), 0),
(4,  1, '菜单管理', 1, '/system/menu', 'system/menu/index',     'system:menu:list',        'tree-table', 3, 1, 0, 10001, NOW(), 10001, NOW(), 0),

-- ── 用户管理按钮（三级）────────────────────────────────────────────
(5,  2, '新增用户', 2, NULL, NULL, 'system:user:add',       NULL, 1, 0, 0, 10001, NOW(), 10001, NOW(), 0),
(6,  2, '编辑用户', 2, NULL, NULL, 'system:user:edit',      NULL, 2, 0, 0, 10001, NOW(), 10001, NOW(), 0),
(7,  2, '删除用户', 2, NULL, NULL, 'system:user:delete',    NULL, 3, 0, 0, 10001, NOW(), 10001, NOW(), 0),
(8,  2, '重置密码', 2, NULL, NULL, 'system:user:reset-pwd', NULL, 4, 0, 0, 10001, NOW(), 10001, NOW(), 0),

-- ── 角色管理按钮 ────────────────────────────────────────────────────
(9,  3, '新增角色', 2, NULL, NULL, 'system:role:add',       NULL, 1, 0, 0, 10001, NOW(), 10001, NOW(), 0),
(10, 3, '编辑角色', 2, NULL, NULL, 'system:role:edit',      NULL, 2, 0, 0, 10001, NOW(), 10001, NOW(), 0),
(11, 3, '删除角色', 2, NULL, NULL, 'system:role:delete',    NULL, 3, 0, 0, 10001, NOW(), 10001, NOW(), 0),

-- ── 菜单管理按钮 ────────────────────────────────────────────────────
(12, 4, '新增菜单', 2, NULL, NULL, 'system:menu:add',       NULL, 1, 0, 0, 10001, NOW(), 10001, NOW(), 0),
(13, 4, '编辑菜单', 2, NULL, NULL, 'system:menu:edit',      NULL, 2, 0, 0, 10001, NOW(), 10001, NOW(), 0),
(14, 4, '删除菜单', 2, NULL, NULL, 'system:menu:delete',    NULL, 3, 0, 0, 10001, NOW(), 10001, NOW(), 0);


-- --------------------------------------------------------------------
-- 用户角色绑定：超管 10001 → ROLE_SUPER_ADMIN
-- --------------------------------------------------------------------
INSERT INTO user_role (id, user_id, role_id, create_by, create_time)
VALUES (1, 10001, 1, 10001, NOW());


-- --------------------------------------------------------------------
-- 角色菜单绑定：ROLE_SUPER_ADMIN 绑定全量平台菜单（id 1~14）
-- --------------------------------------------------------------------
INSERT INTO role_menu (id, role_id, menu_id, create_by, create_time)
VALUES ( 1, 1,  1, 10001, NOW()),
       ( 2, 1,  2, 10001, NOW()),
       ( 3, 1,  3, 10001, NOW()),
       ( 4, 1,  4, 10001, NOW()),
       ( 5, 1,  5, 10001, NOW()),
       ( 6, 1,  6, 10001, NOW()),
       ( 7, 1,  7, 10001, NOW()),
       ( 8, 1,  8, 10001, NOW()),
       ( 9, 1,  9, 10001, NOW()),
       (10, 1, 10, 10001, NOW()),
       (11, 1, 11, 10001, NOW()),
       (12, 1, 12, 10001, NOW()),
       (13, 1, 13, 10001, NOW()),
       (14, 1, 14, 10001, NOW());
