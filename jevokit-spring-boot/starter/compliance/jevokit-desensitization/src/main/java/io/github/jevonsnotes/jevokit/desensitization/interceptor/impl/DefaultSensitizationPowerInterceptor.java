package io.github.jevonsnotes.jevokit.desensitization.interceptor.impl;

import io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel;
import io.github.jevonsnotes.jevokit.desensitization.interceptor.SensitizationPowerInterceptor;

import java.util.List;

/**
 * 脱敏权限判定默认实现：恒返回 false——任何用户都无明文权限，脱敏结果一律生效。
 * 使用方式：@ConditionalOnMissingBean 自动注册，无需配置；需要按角色放行明文时提供自定义
 * {@link SensitizationPowerInterceptor} bean 覆盖（本实现随即退位）。
 * <p>关键约束（为什么恒 false）：默认态下 kit 对用户身份一无所知，无法做任何可靠的权限判定；
 * 安全默认必须选择"全脱敏"而非"全放行"——漏脱敏是数据泄露，多脱敏只是可用性问题。
 */
public class DefaultSensitizationPowerInterceptor implements SensitizationPowerInterceptor {

    /**
     * 恒返回 false（判定语义见类注释）。
     */
    @Override
    public boolean check(DstLevel level, List<String> powerKey) {
        return false;
    }
}
