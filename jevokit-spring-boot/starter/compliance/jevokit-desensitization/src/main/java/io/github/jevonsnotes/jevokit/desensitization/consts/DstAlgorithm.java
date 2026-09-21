package io.github.jevonsnotes.jevokit.desensitization.consts;

/**
 * 脱敏算法枚举：视图与日志两条链路各自独立选择（jevokit.desensitization.algorithm.view / .log）。
 * 使用方式：BASIC 输出 {@code *} 掩码（不可逆）；SM4 输出国密 SM4-CBC 密文（可经 {@code DesUtil.decrypt}
 * 凭权限还原）；MIXED 同时输出密文+掩码（掩码给人看、密文留还原）。
 * <p>关键约束：SM4/MIXED 密钥取全局配置 jevokit.crypto.sm4-key（缺失时 fail-fast）；
 * 密文形态以 {@code @DST(...)} 前缀标识（见 {@link DstConst}），已脱敏的值再次经过脱敏入口会被识别并跳过（幂等）。
 */
public enum DstAlgorithm {
    /**
     * 基础脱敏算法，敏感字符以*替换
     */
    BASIC,
    /**
     * 对称加密算法,基于国密SM4-CBC，可解密
     */
    SM4,
    /**
     * 混合算法，同时返回基础密文+对称加密密文
     */
    MIXED;
}
