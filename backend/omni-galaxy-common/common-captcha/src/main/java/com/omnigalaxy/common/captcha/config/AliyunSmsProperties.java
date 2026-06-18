package com.omnigalaxy.common.captcha.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云短信服务配置。
 * 对应 application.yml 的 {@code captcha.sms.aliyun} 前缀。
 *
 * <p>templateParam 固定为 {@code {"code":"xxxxxx"}}，模板内容需在阿里云控制台
 * 审核通过，变量名必须与此对齐（即 {@code ${code}}）。
 */
@Data
@ConfigurationProperties(prefix = "captcha.sms.aliyun")
public class AliyunSmsProperties {

    /** AccessKey ID */
    private String accessKeyId;

    /** AccessKey Secret */
    private String accessKeySecret;

    /** SMS 签名名称（需在阿里云控制台审核通过） */
    private String signName;

    /** SMS 模板 Code（需在阿里云控制台审核通过） */
    private String templateCode;

    /** 服务接入点，国内默认值即可 */
    private String endpoint = "dysmsapi.aliyuncs.com";
}
