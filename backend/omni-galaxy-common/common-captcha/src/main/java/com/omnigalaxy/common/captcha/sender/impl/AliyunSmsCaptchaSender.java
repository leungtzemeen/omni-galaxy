package com.omnigalaxy.common.captcha.sender.impl;

import com.aliyun.auth.credentials.Credential;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import com.aliyun.sdk.service.dysmsapi20170525.AsyncClient;
import com.aliyun.sdk.service.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.sdk.service.dysmsapi20170525.models.SendSmsResponse;
import com.omnigalaxy.common.captcha.config.AliyunSmsProperties;
import com.omnigalaxy.common.captcha.sender.CaptchaSender;
import darabonba.core.client.ClientOverrideConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 阿里云短信验证码发送器（PHONE 渠道生产实现）。
 *
 * <p>templateParam 固定注入 {@code {"code":"xxxxxx"}}，
 * 对应阿里云控制台模板变量 {@code ${code}}。
 * 实例由 {@link com.omnigalaxy.common.captcha.config.CaptchaAutoConfig} 条件装配，
 * 需配置 {@code captcha.sms.aliyun.access-key-id} 方可激活。
 *
 * <p>使用新版阿里云异步 SDK（alibabacloud-dysmsapi20170525），以 {@link AsyncClient} 提交，
 * 通过 {@link java.util.concurrent.CompletableFuture#get()} 同步等待返回，兼容 Spring MVC 线程模型。
 */
@Slf4j
public class AliyunSmsCaptchaSender implements CaptchaSender {

    private final AliyunSmsProperties properties;
    private final AsyncClient          client;

    public AliyunSmsCaptchaSender(AliyunSmsProperties properties) {
        this.properties = properties;
        this.client     = buildClient(properties);
    }

    @Override
    public void send(String identityType, String identifier, String code) {
        log.info(">>>> [AliyunSMS] 发送短信验证码 identifier: {}", mask(identifier));
        try {
            SendSmsRequest request = SendSmsRequest.builder()
                    .phoneNumbers(identifier)
                    .signName(properties.getSignName())
                    .templateCode(properties.getTemplateCode())
                    .templateParam("{\"code\":\"" + code + "\"}")
                    .build();

            SendSmsResponse response = client.sendSms(request).get(10, TimeUnit.SECONDS);

            // 阿里云以 "OK" 表示提交成功（不代表终端收到，运营商异步投递）
            String responseCode = response.getBody().getCode();
            if (!"OK".equals(responseCode)) {
                log.warn(">>>> [AliyunSMS] 短信提交失败（运营商返回非 OK）code: {} message: {}",
                        responseCode, response.getBody().getMessage());
            } else {
                log.info("<<<< [AliyunSMS] 短信提交成功 identifier: {}", mask(identifier));
            }
        } catch (Exception e) {
            log.error(">>>> [AliyunSMS] 短信发送异常 identifier: {}", mask(identifier), e);
            throw new RuntimeException("短信发送失败，请稍后重试", e);
        }
    }

    private static AsyncClient buildClient(AliyunSmsProperties props) {
        StaticCredentialProvider credProvider = StaticCredentialProvider.create(
                Credential.builder()
                        .accessKeyId(props.getAccessKeyId())
                        .accessKeySecret(props.getAccessKeySecret())
                        .build());
        return AsyncClient.builder()
                .credentialsProvider(credProvider)
                .overrideConfiguration(
                        ClientOverrideConfiguration.create()
                                .setEndpointOverride(props.getEndpoint()))
                .build();
    }

    private String mask(String s) {
        if (s == null || s.length() <= 4) return "****";
        return s.substring(0, 3) + "****" + s.substring(s.length() - 2);
    }
}
