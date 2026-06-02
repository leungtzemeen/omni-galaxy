package com.omnigalaxy.platform.user.service;

public interface UserProfileService {

    Long createProfile(String nickname, String idempotencyKey);
}