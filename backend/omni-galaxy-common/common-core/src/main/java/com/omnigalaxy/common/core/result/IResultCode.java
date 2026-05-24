package com.omnigalaxy.common.core.result;

/**
 * 状态码契约接口。
 * 各业务域（电商、AI、直播）实现此接口以扩展自身专属状态码，遵循开闭原则。
 */
public interface IResultCode {

    int getCode();

    String getMsg();
}
