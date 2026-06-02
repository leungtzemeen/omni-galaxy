package com.omnigalaxy.platform.auth.service;

import com.omnigalaxy.platform.auth.domain.UserCredential;

public interface UserCredentialService {

    UserCredential findByIdentity(String identityType, String identifier);

    void saveCredential(String identityType, String identifier, Long userId);
}