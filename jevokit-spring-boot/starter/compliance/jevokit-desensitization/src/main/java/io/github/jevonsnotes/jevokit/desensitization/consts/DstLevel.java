package io.github.jevonsnotes.jevokit.desensitization.consts;

import cn.hutool.core.util.StrUtil;

/**
 * 脱敏敏感级别枚举（LV0 最敏感 → LV9 最不敏感）：既是字段标注的权限档位
 * （{@code @DstProperty(level=...)}，默认 LV9），也是用户可见密文解密的权限门槛。
 * 使用方式：级别与权限代码的映射经 spkit.desensitization.level-power-key 配置，
 * {@code SensitizationPowerInterceptor#check} 依"用户权限代码 + 字段级别"判定能否查看明文。
 * <p>判定与返回语义：{@link #of(String)} 解析失败或空白时返回 null（容错降级，不抛错）；
 * 编号本身不参与权限比较——能否查看明文完全由 level-power-key 映射与拦截器实现决定，
 * 惯例上编号越小越敏感（LV0 最敏感），配权时应给低编号档位更严格的权限代码。
 */
public enum DstLevel {

    LV0,LV1,LV2,LV3,LV4,LV5,LV6,LV7,LV8,LV9;

    public static DstLevel of(String level) {
        if(StrUtil.isNotBlank(level)) {
            try {
                return DstLevel.valueOf(level);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }
}
