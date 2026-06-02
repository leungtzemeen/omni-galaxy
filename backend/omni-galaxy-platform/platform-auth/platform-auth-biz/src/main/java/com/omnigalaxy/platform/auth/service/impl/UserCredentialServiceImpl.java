package com.omnigalaxy.platform.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omnigalaxy.platform.auth.domain.UserCredential;
import com.omnigalaxy.platform.auth.mapper.UserCredentialMapper;
import com.omnigalaxy.platform.auth.service.UserCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCredentialServiceImpl implements UserCredentialService {

    private final UserCredentialMapper credentialMapper;

    @Override
    @Transactional(readOnly = true)
    public UserCredential findByIdentity(String identityType, String identifier) {
        return credentialMapper.selectOne(
                new LambdaQueryWrapper<UserCredential>()
                        .eq(UserCredential::getIdentityType, identityType)
                        .eq(UserCredential::getIdentifier, identifier)
        );
    }

    @Override
    @Transactional
    public void saveCredential(String identityType, String identifier, Long userId) {
        UserCredential cred = new UserCredential();
        cred.setUserId(userId);
        cred.setIdentityType(identityType);
        cred.setIdentifier(identifier);
        cred.setStatus(0);
        credentialMapper.insert(cred);
    }
}