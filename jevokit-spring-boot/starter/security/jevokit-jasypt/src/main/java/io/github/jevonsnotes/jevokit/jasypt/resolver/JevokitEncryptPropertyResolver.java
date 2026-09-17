package io.github.jevonsnotes.jevokit.jasypt.resolver;

import com.ulisesbocchio.jasyptspringboot.EncryptablePropertyDetector;
import com.ulisesbocchio.jasyptspringboot.EncryptablePropertyResolver;
import com.ulisesbocchio.jasyptspringboot.detector.DefaultPropertyDetector;
import com.ulisesbocchio.jasyptspringboot.exception.DecryptionException;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.exceptions.EncryptionOperationNotPossibleException;
import org.springframework.util.Assert;

/**
 * jevokit 配置属性解密解析器：在 Spring 属性绑定阶段识别 {@code ENC(...)} 密文并解密为明文。
 * 使用方式：由 {@link io.github.jevonsnotes.jevokit.jasypt.config.JevokitJasyptConfiguration} 注册，
 * 替代 jasypt-spring-boot 默认解析器；是否为密文由注入的 {@link EncryptablePropertyDetector} 判定
 * （默认 {@link DefaultPropertyDetector}：{@code ENC(} 前缀 + {@code )} 后缀）。
 * <p>判定与返回语义：非密文原样返回（不触碰普通属性）；密文先 trim 再解密；
 * 解密失败（口令不匹配/密文损坏）抛 {@link DecryptionException} fail-fast，防止把密文串当明文流入业务。
 */
public class JevokitEncryptPropertyResolver implements EncryptablePropertyResolver {

    private StringEncryptor encryptor;
    private EncryptablePropertyDetector detector;

    /** 使用默认探测器（ENC() 前后缀）构造。 */
    public JevokitEncryptPropertyResolver(StringEncryptor encryptor) {
        this(encryptor, new DefaultPropertyDetector());
    }

    /**
     * 全参构造。
     *
     * @param encryptor 解密用的加密器（不可为 null）
     * @param detector  密文探测器（判定属性值是否为密文，不可为 null）
     */
    public JevokitEncryptPropertyResolver(StringEncryptor encryptor, EncryptablePropertyDetector detector) {
        Assert.notNull(encryptor, "String encryptor can't be null");
        Assert.notNull(detector, "Encryptable Property detector can't be null");
        this.encryptor = encryptor;
        this.detector = detector;
    }

    /**
     * 解析单个属性值（判定与返回语义见类注释：非密文直通、密文解密、失败抛 {@link DecryptionException}）。
     *
     * @param value 原始属性值
     * @return 密文解密后的明文；非密文原样返回
     */
    @Override
    public String resolvePropertyValue(String value) {
        String actualValue = value;
        if (detector.isEncrypted(value)) {
            try {
                actualValue = encryptor.decrypt(detector.unwrapEncryptedValue(value.trim()));
            } catch (EncryptionOperationNotPossibleException e) {
                throw new DecryptionException("Decryption of Properties failed,  make sure encryption/decryption " +
                        "passwords match", e);
            }
        }
        return actualValue;
    }
}
