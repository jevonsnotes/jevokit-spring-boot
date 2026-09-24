package io.github.jevonsnotes.jevokit.configuration.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 加解密密钥配置（jevokit.crypto.*）。
 *
 * <p>替代 SecurityUtil 已移除的内置默认密钥（原内置密钥随源码泄露，视为不可信，禁止再用于新数据）。
 * 未配置的算法，其无参便捷方法（如 {@code SecurityUtil.aesEncrypt(val)}）将抛出
 * {@link IllegalStateException}，异常消息内嵌对应配置键名；也可改用显式 key 重载免配置调用。
 * 默认 {@code startup-check=true}：启动期校验四个密钥，任一缺失即启动失败（fail-fast，详见该字段注释）。
 *
 * <p>密钥值请通过环境变量、外部配置文件（如不入库的 application-local.yml）等机制注入，避免提交进仓库。
 */
@ConfigurationProperties("jevokit.crypto")
public class CryptoProperties {

    /** RSA 公钥（Base64）。建议密钥位数 &ge;2048；仅供演示/自测用途时也勿复用历史泄露密钥 */
    private String rsaPublicKey;

    /** RSA 私钥（Base64） */
    private String rsaPrivateKey;

    /** AES 密钥（16/24/32 字节） */
    private String aesKey;

    /** SM4 密钥（16 字节） */
    private String sm4Key;

    /**
     * 启动校验开关（默认 true）：开启时任一密钥缺失，应用启动即失败，
     * 异常消息一次性列出全部缺失配置键（fail-fast，配漏在启动期暴露而非生产运行期）。
     * 关闭时退回零负担模式——缺配置不拦启动，延迟到首次使用无参 API 时才抛 IllegalStateException
     * （适合只用到部分算法、或明确接受延迟失败的应用）。
     * <p>默认从严的取舍：密钥配漏属于"上线前就该发现"的装配错误，宁可在启动期误伤（多报不用的密钥缺失），
     * 也不要把失败推迟到生产流量触达加密链路的那一刻；确需零负担的应用显式配 false 关闭。
     */
    private boolean startupCheck = true;

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

    public boolean isStartupCheck() {
        return startupCheck;
    }

    public void setStartupCheck(boolean startupCheck) {
        this.startupCheck = startupCheck;
    }
}
