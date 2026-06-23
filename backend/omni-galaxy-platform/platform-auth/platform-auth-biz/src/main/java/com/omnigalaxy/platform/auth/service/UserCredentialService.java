package com.omnigalaxy.platform.auth.service;

import com.omnigalaxy.platform.auth.domain.UserCredential;

public interface UserCredentialService {

    UserCredential findByIdentity(String identityType, String identifier);

    /** 跨 identityType 查询同一 identifier 是否已被任意方式注册（用于注册碰撞检测）。 */
    UserCredential findAnyByIdentifier(String identifier);

    /** 按 userId + 凭证类型查询，用于修改密码时定位 PASSWORD 凭证行是否存在。 */
    UserCredential findByUserIdAndType(Long userId, String identityType);

    void saveCredential(String identityType, String identifier, Long userId);

    void saveCredential(String identityType, String identifier, Long userId, String hashedCredential);

    /** 更新指定用户的 PASSWORD 凭证哈希；调用方须确保该行已存在。 */
    void updatePassword(Long userId, String hashedPassword);
}