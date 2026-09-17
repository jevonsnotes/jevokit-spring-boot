package io.github.jevonsnotes.jevokit.desensitization.config;

import lombok.Data;

/**
 * 脱敏解密 API（/jevokit/desensitization/*）的访问控制配置（预留）。
 * 使用方式：enabled 打开 token 准入并配置 accessToken；但配套的 DesensitizationApiInterceptor
 * 当前未注册（见 JevokitDesensitizationConfiguration 中注释停用的装配代码），本配置暂无消费方，
 * 生效前解密端点的防护依赖业务侧网关/鉴权。
 * <p>关键约束：解密端点可直接还原密文，放开 enabled 前必须先恢复配套拦截器并确认端点暴露面。
 */
@Data
public class DesensitizationApiProperties {
    private boolean enabled = false;
    private String accessToken;
}
