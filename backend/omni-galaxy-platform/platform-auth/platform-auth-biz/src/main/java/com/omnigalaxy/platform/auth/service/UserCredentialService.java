package com.omnigalaxy.platform.auth.service;

import com.omnigalaxy.platform.auth.domain.UserCredential;

public interface UserCredentialService {

    UserCredential findByIdentity(String identityType, String identifier);

    /** 跨 identityType 查询同一 identifier 是否已被任意方式注册（用于注册碰撞检测）。 */
    UserCredential findAnyByIdentifier(String identifier);

    void saveCredential(String identityType, String identifier, Long userId);

    void saveCredential(String identityType, String identifier, Long userId, String hashedCredential);
}