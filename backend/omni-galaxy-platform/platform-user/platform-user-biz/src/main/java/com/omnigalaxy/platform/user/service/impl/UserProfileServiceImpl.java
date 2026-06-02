package com.omnigalaxy.platform.user.service.impl;

import com.omnigalaxy.platform.user.domain.UserProfile;
import com.omnigalaxy.platform.user.mapper.UserProfileMapper;
import com.omnigalaxy.platform.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private static final String   IDEMPOTENCY_KEY_PREFIX = "create:idempotency:";
    private static final Duration IDEMPOTENCY_TTL        = Duration.ofMinutes(10);

    private final UserProfileMapper    profileMapper;
    private final StringRedisTemplate  redisTemplate;

    @Override
    @Transactional
    public Long createProfile(String nickname, String idempotencyKey) {
        // 幂等快速路径：Redis 命中则直接返回，无需开事务（Spring @Transactional 仅在方法体内有效，
        // 但此处读取发生在事务开启后，问题不大；关键点是 Redis 写入使用 afterCommit 钩子）
        if (idempotencyKey != null) {
            String cached = redisTemplate.opsForValue().get(IDEMPOTENCY_KEY_PREFIX + idempotencyKey);
            if (cached != null) {
                log.info("<<<< [UserProfile] 幂等命中，复用已有档案 userId: {}", cached);
                return Long.parseLong(cached);
            }
        }

        UserProfile profile = new UserProfile();
        profile.setNickname(nickname != null ? nickname : generateDefaultNickname());
        profile.setGender(0);
        profile.setStatus(0);
        profileMapper.insert(profile);
        Long userId = profile.getId();

        // 事务提交后再写 Redis，确保 DB rollback 时不会产生指向幽灵 ID 的缓存
        if (idempotencyKey != null) {
            String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
            String userIdStr = String.valueOf(userId);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    redisTemplate.opsForValue().set(redisKey, userIdStr, IDEMPOTENCY_TTL);
                    log.info(">>>> [UserProfile] 幂等键已缓存 key: {} userId: {}", redisKey, userIdStr);
                }
            });
        }

        log.info("<<<< [UserProfile] 用户档案已创建 userId: {}", userId);
        return userId;
    }

    private String generateDefaultNickname() {
        return "用户" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000);
    }
}
