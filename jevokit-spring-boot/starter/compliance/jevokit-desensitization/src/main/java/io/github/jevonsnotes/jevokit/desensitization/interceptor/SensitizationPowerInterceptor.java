package io.github.jevonsnotes.jevokit.desensitization.interceptor;

import io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel;

import java.util.List;

/**
 * 脱敏权限判定 SPI：决定"当前用户 + 敏感级别"能否查看明文（脱敏体系唯一的放行闸门）。
 * 使用方式：由 {@code JevokitDesensitizationConfiguration} 以 @ConditionalOnMissingBean 注册默认实现，
 * 业务实现本接口注册为 bean 即可替换；典型实现从安全上下文取当前用户权限代码，与
 * spkit.desensitization.level-power-key 映射出的 powerKey 比对。
 * <p>判定与返回语义：true = 允许查看该级别明文（ENCRYPT 档出口放行原文 / DECRYPT 档解密还原）；
 * false = 保持脱敏。默认实现恒返回 false——不放行任何明文（安全默认，宁严勿漏）。
 */
public interface SensitizationPowerInterceptor {

    /**
     * 权限判定。
     *
     * @param level    敏感级别（来自字段标注或密文内嵌级别）
     * @param powerKey 该级别配置的权限代码集合（level-power-key 映射，供比对，可为 null/空）
     * @return true 表示允许查看明文
     */
    boolean check(DstLevel level,List<String> powerKey);
}
