package com.omnigalaxy.platform.auth.component;

import com.omnigalaxy.common.core.exception.BizException;
import com.omnigalaxy.common.core.result.Result;
import com.omnigalaxy.common.core.result.ResultCodeEnum;
import com.omnigalaxy.platform.auth.api.result.AuthResultCodeEnum;
import com.omnigalaxy.platform.auth.domain.UserCredential;
import com.omnigalaxy.platform.auth.service.UserCredentialService;
import com.omnigalaxy.platform.user.api.client.UserProfileClient;
import com.omnigalaxy.platform.user.api.dto.UserProfileCreateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 账户生命周期管理器：全渠道账户创建、查找与凭证合并的核心安全内核。
 *
 * <h3>核心职责</h3>
 * <ul>
 *   <li>提供跨渠道（OTP、密码、OAuth、小程序）的统一账户发现与注册路径。</li>
 *   <li>通过 Redis SetNX 分布式锁 + double-check 模式防止并发注册产生数据撕裂。</li>
 *   <li>通过 {@link DataIntegrityViolationException} 物理自愈防止极端并发下的 HTTP 500 外溢。</li>
 *   <li>管理 NEED_BIND 流程中临时 bindTicket 的签发与一次性消费。</li>
 * </ul>
 *
 * <h3>强依赖契约（调用方必须保证）</h3>
 * <p>{@code UserProfileClient.createProfile} 必须对相同 {@code idempotencyKey} 严格幂等——
 * 即并发或重试调用返回同一 userId——否则在锁 TTL 过期的极端场景下将产生无法回收的僵尸 userId。</p>
 *
 * <h3>锁键命名空间</h3>
 * <pre>{@code reg:lock:{identifier}}</pre>
 * <p>所有注册路径统一使用 identifier（手机号 / unionId / 邮箱）作为锁键后缀，
 * 保证同一真实用户无论通过何种渠道并发注册都落在同一把锁下，彻底消除跨渠道竞态。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountLifecycleManager {

    private static final String   REG_LOCK_PREFIX    = "reg:lock:";
    private static final String   BIND_TICKET_PREFIX = "bind:ticket:";
    private static final Duration REG_LOCK_TTL       = Duration.ofSeconds(10);
    private static final Duration BIND_TICKET_TTL    = Duration.ofMinutes(10);

    // BindTicketPayload 各字段间使用 SOH（）分隔，手机号/unionId 中均不含此字符
    private static final String   TICKET_DELIMITER   = "";

    private final UserCredentialService credentialService;
    private final UserProfileClient     userProfileClient;
    private final StringRedisTemplate   redisTemplate;

    // =========================================================================
    // 公开 API
    // =========================================================================

    /**
     * OTP 一键登注路径（Strategy A：合并优先）。
     *
     * <p>账户发现顺序：
     * <ol>
     *   <li>同类型凭证存在 → 直接返回 userId（老用户，本方法调用前已由 AuthService 快速路径处理，此处为锁内 double-check）</li>
     *   <li>同 identifier 存在其他类型凭证 → 追加新凭证行并复用既有 userId（跨渠道合并，如 OTP 登录遇到密码注册老用户）</li>
     *   <li>全新用户 → RPC 建档 → 短写事务写凭证</li>
     * </ol>
     *
     * @param identityType 凭证类型（PHONE / EMAIL）
     * @param identifier   凭证标识符（E.164 手机号或邮箱地址）
     * @return 对应的 userId
     */
    public Long findOrMergeAccount(String identityType, String identifier) {
        return withLock(
            identifier,
            () -> {
                // double-check：防锁等待期间已由其他线程完成注册
                UserCredential cred = credentialService.findByIdentity(identityType, identifier);
                if (cred != null) return cred.getUserId();

                // 跨类型老用户识别：OTP 验证已证明 identifier 归属权，追加凭证行并复用 userId
                UserCredential crossCred = credentialService.findAnyByIdentifier(identifier);
                if (crossCred != null) {
                    log.info(">>>> [Auth] 跨类型老用户识别，追加凭证复用账号 newType: {} userId: {}",
                            identityType, crossCred.getUserId());
                    credentialService.saveCredential(identityType, identifier, crossCred.getUserId());
                    return crossCred.getUserId();
                }

                // 全新用户：RPC 建档（幂等键保障并发安全）→ 短写事务写凭证
                Long userId = createUserProfile(identityType, identifier);
                credentialService.saveCredential(identityType, identifier, userId);
                return userId;
            },
            () -> {
                // DIV 物理自愈：re-read 拿到抢先写入的凭证，无感放行
                UserCredential c = credentialService.findByIdentity(identityType, identifier);
                return c != null ? c.getUserId() : null;
            },
            identityType
        );
    }

    /**
     * 账密注册路径（Strategy B：碰撞拒绝）。
     *
     * <p>与 {@link #findOrMergeAccount} 的关键语义差异：任何已存在的同 identifier 凭证均视为冲突，
     * 抛出精确业务异常引导用户走登录路径，禁止隐式合并。
     *
     * <p>包含乐观路径（锁外快速碰撞检测）+ 悲观路径（锁内 double-check），兼顾性能与并发安全。
     *
     * @param identityType     凭证类型（PHONE / EMAIL）
     * @param identifier       凭证标识符
     * @param hashedCredential BCrypt 哈希后的密码
     * @return 新账号的 userId
     */
    public Long registerNewAccountWithCredential(
            String identityType, String identifier, String hashedCredential) {
        // 乐观路径：锁外快速碰撞检测，减少锁内 QPS 压力
        checkIdentifierCollision(identifier);

        return withLock(
            identifier,
            () -> {
                // double-check：锁等待期间可能已被其他请求抢注
                checkIdentifierCollision(identifier);
                Long userId = createUserProfile(identityType, identifier);
                credentialService.saveCredential(identityType, identifier, userId, hashedCredential);
                return userId;
            },
            () -> {
                // DIV 物理自愈：唯一约束冲突说明已被抢注，抛业务异常（绝不返回他人账号）
                throw new BizException(AuthResultCodeEnum.IDENTIFIER_ALREADY_REGISTERED);
            },
            identityType
        );
    }

    /**
     * 携带辅助凭证 hint 的多凭证登注路径（OAuth / 小程序场景）。
     *
     * <p>信任域决策规则：
     * <ul>
     *   <li>{@link TrustLevel#EXTERNAL} hint 与系统已有 OWN 凭证碰撞 →
     *       不建档、不签发 JWT，签发 {@code bindTicket}，引导用户完成 OTP 二次验证后绑定，
     *       防止"鸠占鹊巢"越权登录。</li>
     *   <li>hint 对应 identifier 在系统中不存在 → 与主凭证同步原子落地，无感注册。</li>
     * </ul>
     *
     * @param primaryType 主凭证类型（WECHAT / ALIPAY）
     * @param primaryId   主凭证 identifier（unionId 等）
     * @param hints       辅助凭证列表（如小程序解密出的手机号）
     * @return {@link SocialLoginResult}；status=NEED_BIND 时调用方禁止签发 JWT
     */
    public SocialLoginResult findOrMergeAccountWithHints(
            String primaryType, String primaryId, List<CredentialHint> hints) {
        return withLock(
            primaryId,
            () -> {
                // 快速路径：主凭证已存在 → 直接登录，hints 不自动追加（避免未经用户确认的静默绑定）
                UserCredential primaryCred = credentialService.findByIdentity(primaryType, primaryId);
                if (primaryCred != null) {
                    return SocialLoginResult.login(primaryCred.getUserId());
                }

                // 全量扫描所有 EXTERNAL hints，收集完整冲突图后再决策
                // 禁止短路返回：提前 return 会掩盖后续 hint 的碰撞，导致跨账号归属不明的灰色地带
                Map<CredentialHint, Long> conflictMap = new LinkedHashMap<>();
                for (CredentialHint hint : hints) {
                    if (hint.trustLevel() != TrustLevel.EXTERNAL) continue;
                    UserCredential existing = credentialService.findByIdentity(
                            hint.identityType(), hint.identifier());
                    if (existing != null) {
                        conflictMap.put(hint, existing.getUserId());
                    }
                }

                if (!conflictMap.isEmpty()) {
                    Set<Long> targetUserIds = new HashSet<>(conflictMap.values());
                    if (targetUserIds.size() > 1) {
                        // 不同 hint 指向不同既有账号 → 账号归属不明，风控硬拒，拒绝一切自动绑定
                        log.warn(">>>> [Auth] 多辅助凭证跨账号碰撞，风控拒绝 primaryType: {} hintConflicts: {} distinctAccounts: {}",
                                primaryType, conflictMap.size(), targetUserIds.size());
                        throw new BizException(AuthResultCodeEnum.MULTIPLE_ACCOUNT_CONFLICT);
                    }
                    // 所有冲突指向同一 userId → 账号归属明确，取首个 hint 作为 OTP 验证锚点
                    CredentialHint bindHint = conflictMap.keySet().iterator().next();
                    Long targetUserId = targetUserIds.iterator().next();
                    String ticket = issueBindTicket(primaryType, primaryId, bindHint);
                    log.warn(">>>> [Auth] 信任域冲突，下发 bindTicket 引导验证 primaryType: {} conflictHints: {} targetUserId: {}",
                            primaryType, conflictMap.size(), targetUserId);
                    return SocialLoginResult.needBind(ticket, maskIdentifier(bindHint.identifier()));
                }

                // 全量落地：全新用户，原子写入主凭证 + 所有辅助凭证
                Long userId = createUserProfile(primaryType, primaryId);
                credentialService.saveCredential(primaryType, primaryId, userId);
                for (CredentialHint hint : hints) {
                    try {
                        credentialService.saveCredential(hint.identityType(), hint.identifier(), userId);
                    } catch (DataIntegrityViolationException ex) {
                        // hint 凭证被并发抢占（极端情况）：跳过，不影响主凭证落地
                        log.warn(">>>> [Auth] hint 凭证唯一约束（极端并发），降级跳过 hintType: {}",
                                hint.identityType());
                    }
                }
                return SocialLoginResult.registeredFull(userId);
            },
            () -> {
                // DIV 物理自愈：主凭证被并发写入，re-read 复用结果
                UserCredential c = credentialService.findByIdentity(primaryType, primaryId);
                return c != null ? SocialLoginResult.login(c.getUserId()) : null;
            },
            primaryType
        );
    }

    /**
     * 预检 bindTicket 的类型与标识符一致性（只 GET 不消费），必须在 OTP 校验前调用。
     *
     * <p>防御目的：阻止攻击者以错误 identityType（如 EMAIL）配合自己的合法 OTP 绕过 OTP 语义校验，
     * 同时避免 ticket 在 {@link #consumeBindTicket} 的 getAndDelete 中被错误请求提前销毁（低烈度 DoS）。
     *
     * <p>本方法使用 Redis {@code GET}（非 getAndDelete），不消费 ticket，
     * 类型/标识符不符时直接拒绝且 ticket 保持有效，用户可重新提交正确参数。
     *
     * @param ticket       bindTicket（一次性票据 UUID）
     * @param identityType 请求方声称的凭证类型（PHONE / EMAIL），必须与票据内 conflictType 一致
     * @param identifier   请求方声称的凭证标识符，必须与票据内 conflictIdentifier 一致
     * @throws BizException {@link AuthResultCodeEnum#BIND_TICKET_EXPIRED} ticket 不存在或已过期
     * @throws BizException {@link AuthResultCodeEnum#BIND_IDENTIFIER_MISMATCH} 类型或标识符不符
     */
    public void preValidateBindTicket(String ticket, String identityType, String identifier) {
        // GET（非 getAndDelete）：预检不消费 ticket，校验失败时 ticket 仍可用
        String ticketData = redisTemplate.opsForValue().get(BIND_TICKET_PREFIX + ticket);
        if (ticketData == null) {
            throw new BizException(AuthResultCodeEnum.BIND_TICKET_EXPIRED);
        }
        BindTicketPayload payload = deserializeTicket(ticketData);
        if (!payload.conflictType().equals(identityType) ||
            !payload.conflictIdentifier().equals(identifier)) {
            throw new BizException(AuthResultCodeEnum.BIND_IDENTIFIER_MISMATCH);
        }
    }

    /**
     * 消费 bindTicket，完成跨信任域账号绑定（NEED_BIND 自愈路径）。
     *
     * <p><strong>调用前置条件</strong>：调用方必须已完成入参 {@code verifiedIdentifier} 的 OTP 校验，
     * 本方法信任该 identifier 的归属权已在本系统 OWN 信任域内确认。
     *
     * <p>执行后效果：社交凭证（如 WECHAT/unionId）追加到既有账号下，零脏数据，零新建档案。
     *
     * @param ticket             bindTicket（一次性，10 分钟有效）
     * @param verifiedIdentifier 已通过 OTP 验证的 identifier（E.164 手机号或邮箱，由 ticket 中 conflictType 决定类型）
     * @return 绑定后的 userId（即既有账号的 userId）
     */
    public Long consumeBindTicket(String ticket, String verifiedIdentifier) {
        // 原子消费：getAndDelete 保证 ticket 一次性，防重放
        String ticketData = redisTemplate.opsForValue().getAndDelete(BIND_TICKET_PREFIX + ticket);
        if (ticketData == null) {
            throw new BizException(AuthResultCodeEnum.BIND_TICKET_EXPIRED);
        }
        BindTicketPayload payload = deserializeTicket(ticketData);

        // 防换号攻击：ticket 内记录的 conflictIdentifier 必须与入参完全吻合
        if (!payload.conflictIdentifier().equals(verifiedIdentifier)) {
            throw new BizException(AuthResultCodeEnum.BIND_IDENTIFIER_MISMATCH);
        }

        // 以 conflictIdentifier 为锁键，保证目标账号在绑定写入期间处于静止互斥状态
        return withLock(
            payload.conflictIdentifier(),
            () -> {
                // 动态使用 ticket 中记录的 conflictType，支持 PHONE / EMAIL 等多渠道扩展
                UserCredential conflictCred = credentialService.findByIdentity(
                        payload.conflictType(), payload.conflictIdentifier());
                if (conflictCred == null) {
                    // OTP 通过后账号被极端删除，理论上不可能，防御性兜底
                    throw new BizException(AuthResultCodeEnum.ACCOUNT_NOT_FOUND);
                }
                Long targetUserId = conflictCred.getUserId();

                // double-check：防并发重复绑定
                UserCredential existingPrimary = credentialService.findByIdentity(
                        payload.primaryType(), payload.primaryId());
                if (existingPrimary != null) {
                    // 已绑定到同一账号 → 幂等成功
                    if (existingPrimary.getUserId().equals(targetUserId)) return targetUserId;
                    // 社交账号已被他人抢先绑定到不同账号 → 硬拒
                    throw new BizException(AuthResultCodeEnum.SOCIAL_ACCOUNT_BOUND_TO_ANOTHER);
                }

                // 追加社交凭证到既有账号，零建档，零脏数据
                credentialService.saveCredential(payload.primaryType(), payload.primaryId(), targetUserId);
                log.info("<<<< [Auth] 账号绑定成功 primaryType: {} userId: {}",
                        payload.primaryType(), targetUserId);
                return targetUserId;
            },
            () -> {
                // DIV 物理自愈：社交凭证被并发绑定，re-read 检查归属
                UserCredential c = credentialService.findByIdentity(payload.primaryType(), payload.primaryId());
                return c != null ? c.getUserId() : null;
            },
            payload.primaryType()
        );
    }

    /**
     * 账密注册碰撞检测（Strategy B 决策入口）。
     *
     * <p>跨 identityType 查询同一 identifier 是否已被任意渠道注册，
     * 根据已有凭证类型给出精确的分流提示，避免用户困惑。
     *
     * @throws BizException {@link AuthResultCodeEnum#IDENTIFIER_ALREADY_REGISTERED} 已有密码账号
     * @throws BizException {@link AuthResultCodeEnum#IDENTIFIER_OTP_REGISTERED} 已有 OTP 账号，需先 OTP 登录后绑定密码
     */
    public void checkIdentifierCollision(String identifier) {
        UserCredential existing = credentialService.findAnyByIdentifier(identifier);
        if (existing == null) return;
        if ("PASSWORD".equals(existing.getIdentityType())) {
            throw new BizException(AuthResultCodeEnum.IDENTIFIER_ALREADY_REGISTERED);
        }
        throw new BizException(AuthResultCodeEnum.IDENTIFIER_OTP_REGISTERED);
    }

    // =========================================================================
    // 私有：分布式锁模板
    // =========================================================================

    /**
     * 统一分布式锁闭包模板。
     *
     * <p>执行流程：SetNX 获锁 → 执行 {@code action} → finally 释放锁。
     * 发生 {@link DataIntegrityViolationException} 时执行 {@code selfHeal}（物理自愈）：
     * <ul>
     *   <li>{@code selfHeal} 返回非 null → 自愈成功，安全返回，无 HTTP 500 外溢</li>
     *   <li>{@code selfHeal} 抛出 {@link BizException} → 直接传播（业务决策层的主动拒绝）</li>
     *   <li>{@code selfHeal} 返回 null → 非唯一键约束冲突引起的其他 DB 异常，向上抛原始异常</li>
     * </ul>
     *
     * @param identifier 用于构造锁键的标识符（不含前缀）
     * @param action     主业务逻辑
     * @param selfHeal   DIV 发生时的原地物理自愈逻辑
     * @param logType    告警日志中的类型标识，便于快速定位
     */
    private <T> T withLock(
            String identifier,
            Supplier<T> action,
            Supplier<T> selfHeal,
            String logType) {

        String lockKey = REG_LOCK_PREFIX + identifier;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", REG_LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BizException(ResultCodeEnum.TOO_MANY_REQUESTS, 1L);
        }
        try {
            return action.get();
        } catch (DataIntegrityViolationException ex) {
            log.warn(">>>> [Auth] 凭证唯一约束触发（极端并发），原地物理自愈 type: {}", logType);
            T healed = selfHeal.get();
            if (healed != null) return healed;
            // re-read 也无结果 → 非唯一键约束引起的 DB 故障，向上抛
            throw ex;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    // =========================================================================
    // 私有：工具方法
    // =========================================================================

    private Long createUserProfile(String identityType, String identifier) {
        UserProfileCreateDTO dto = new UserProfileCreateDTO();
        dto.setIdempotencyKey(buildIdempotencyKey(identityType, identifier));
        Result<Long> result = userProfileClient.createProfile(dto);
        return result.getData();
    }

    /** SHA-256(identityType:identifier) 十六进制字符串，作为 createProfile 的不透明幂等键。 */
    private static String buildIdempotencyKey(String identityType, String identifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((identityType + ":" + identifier).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 在任何 JVM 实现中均可用，此分支不可达
        }
    }

    private String issueBindTicket(String primaryType, String primaryId, CredentialHint hint) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        // 使用 SOH（）分隔，该字符不存在于手机号、邮箱、unionId 等合法标识符中
        String payload = String.join(TICKET_DELIMITER,
                primaryType, primaryId, hint.identityType(), hint.identifier());
        redisTemplate.opsForValue().set(BIND_TICKET_PREFIX + ticket, payload, BIND_TICKET_TTL);
        return ticket;
    }

    private BindTicketPayload deserializeTicket(String data) {
        String[] parts = data.split(TICKET_DELIMITER, 4);
        if (parts.length != 4) {
            // ticket 格式异常，视同过期处理
            throw new BizException(AuthResultCodeEnum.BIND_TICKET_EXPIRED);
        }
        return new BindTicketPayload(parts[0], parts[1], parts[2], parts[3]);
    }

    private String maskIdentifier(String s) {
        if (s == null || s.length() <= 4) return "****";
        return s.substring(0, 3) + "****" + s.substring(s.length() - 2);
    }

    // =========================================================================
    // 内部数据结构
    // =========================================================================

    /** bindTicket 的 Redis 存储载体，字段顺序与 TICKET_DELIMITER 分隔的序列化格式对应。 */
    private record BindTicketPayload(
            String primaryType,
            String primaryId,
            String conflictType,
            String conflictIdentifier) {}
}