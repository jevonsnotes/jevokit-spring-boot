package io.github.jevonsnotes.jevokit.desensitization.consts;

/**
 * @Description TODO
 * @Date 2024/11/20
 * @Version 0.0.1
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
