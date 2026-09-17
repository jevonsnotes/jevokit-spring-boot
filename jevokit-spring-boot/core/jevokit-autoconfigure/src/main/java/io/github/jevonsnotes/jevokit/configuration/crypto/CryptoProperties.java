package io.github.jevonsnotes.jevokit.configuration.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 加解密密钥配置（spkit.crypto.*）。
 *
 * <p>替代 SecurityUtil 已移除的内置默认密钥（原内置密钥随源码泄露，视为不可信，禁止再用于新数据）。
 * 未配置的算法，其无参便捷方法（如 {@code SecurityUtil.aesEncrypt(val)}）将抛出
 * {@link IllegalStateException}，异常消息内嵌对应配置键名；也可改用显式 key 重载免配置调用。
 *
 * <p>密钥值请通过环境变量、外部配置文件（如不入库的 application-local.yml）等机制注入，避免提交进仓库。
 */
@ConfigurationProperties("spkit.crypto")
public class CryptoProperties {

    /** RSA 公钥（Base64）。建议密钥位数 &ge;2048；仅供演示/自测用途时也勿复用历史泄露密钥 */
    private String rsaPublicKey;

    /** RSA 私钥（Base64） */
    private String rsaPrivateKey;

    /** AES 密钥（16/24/32 字节） */
    private String aesKey;

    /** SM4 密钥（16 字节） */
    private String sm4Key;

    public String getRsaPublicKey() {
        return rsaPublicKey;
    }

    public void setRsaPublicKey(String rsaPublicKey) {
        this.rsaPublicKey = rsaPublicKey;
    }

    public String getRsaPrivateKey() {
        return rsaPrivateKey;
    }

    public void setRsaPrivateKey(String rsaPrivateKey) {
        this.rsaPrivateKey = rsaPrivateKey;
    }

    public String getAesKey() {
        return aesKey;
    }

    public void setAesKey(String aesKey) {
        this.aesKey = aesKey;
    }

    public String getSm4Key() {
        return sm4Key;
    }

    public void setSm4Key(String sm4Key) {
        this.sm4Key = sm4Key;
    }
}
