package io.github.jevonsnotes.jevokit.jasypt.encryptor;

import cn.hutool.core.util.StrUtil;
import com.ulisesbocchio.jasyptspringboot.util.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.env.Environment;

import java.util.Optional;
import java.util.function.Supplier;

import static com.ulisesbocchio.jasyptspringboot.util.Functional.tap;

/**
 * jevokit 默认字符串加密器：基于 jasypt PooledPBEStringEncryptor 的懒加载单例实现，
 * 承担配置密文（{@code ENC(...)}）的加解密。
 * 使用方式：由 {@link io.github.jevonsnotes.jevokit.jasypt.config.JevokitJasyptConfiguration} 以
 * {@code jevokitStringEncryptor} 为名注册，一般不直接使用；口令与算法参数取 Spring Environment 的
 * {@code jasypt.encryptor.*} 标准键（支持驼峰/中划线变体）。
 * <p>关键约束：
 * <ul>
 *   <li>懒加载：首次 encrypt/decrypt 才真正初始化底层加密器（Singleton 包装），避免启动期无谓校验；</li>
 *   <li>fail-fast：口令未配置（含 yml 中 {@code ${JASYPT_KEY:}} 缺省注入的空串）时抛
 *       {@link IllegalStateException} 终止启动，绝不以空口令静默运行（见 {@code getRequiredPassword}）；</li>
 *   <li>算法等参数缺失时降级为 jasypt 官方默认值并打印 INFO 日志，保证与官方 starter 行为一致。</li>
 * </ul>
 */
@Slf4j
public class JevokitStringEncryptor implements StringEncryptor {
    private final static String JEP_PROPERTY_KEY = "jasypt.encryptor.jep";
    private final static String PASSWORD_PROPERTY_KEY = "jasypt.encryptor.password";
    private final Singleton<StringEncryptor> singleton;

    /**
     * 自定义加密器优先构造：容器中存在指定名的 StringEncryptor bean 则委托之，否则回退默认 PBE 加密器。
     *
     * @param e                     Spring 环境（读取 jasypt.encryptor.* 配置）
     * @param customEncryptorBeanName 自定义加密器 bean 名（不存在时自动回退默认实现）
     * @param bf                    bean 工厂（按名查找自定义加密器）
     */
    public JevokitStringEncryptor(final Environment e, final String customEncryptorBeanName, final BeanFactory bf) {
        singleton = new Singleton<>(() ->
                Optional.of(customEncryptorBeanName)
                        .filter(bf::containsBean)
                        .map(name -> (StringEncryptor) bf.getBean(name))
                        .map(tap(bean -> log.info("Found Custom Encryptor Bean {} with name: {}", bean, customEncryptorBeanName)))
                        .orElseGet(() -> {
                            log.info("String Encryptor custom Bean not found with name '{}'. Initializing Default String Encryptor", customEncryptorBeanName);
                            return createDefault(e);
                        }));
    }

    /** 直接构造默认 PBE 加密器（参数与口令语义见类注释）。 */
    public JevokitStringEncryptor(Environment e) {
        singleton = new Singleton<>(() -> createDefault(e));
    }

    /**
     * 创建默认 PooledPBE 加密器：参数取 jasypt.encryptor.*（缺失降级 jasypt 官方默认值），
     * 口令强制必填（见 getRequiredPassword）。
     */
    private StringEncryptor createDefault(Environment e) {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(getRequiredPassword(e));
        config.setAlgorithm(getProperty(e, "jasypt.encryptor.algorithm", "PBEWithMD5AndDES"));
        config.setKeyObtentionIterations(getProperty(e, "jasypt.encryptor.keyObtentionIterations", "1000"));
        config.setPoolSize(getProperty(e, "jasypt.encryptor.poolSize", "1"));
        config.setProviderName(getProperty(e, "jasypt.encryptor.providerName", "SunJCE"));
        config.setProviderClassName(getProperty(e, "jasypt.encryptor.providerClassName", null));
        config.setSaltGeneratorClassName(getProperty(e, "jasypt.encryptor.saltGeneratorClassname", "org.jasypt.salt.RandomSaltGenerator"));
        config.setIvGeneratorClassName(getProperty(e, "jasypt.encryptor.ivGeneratorClassname", "org.jasypt.iv.RandomIvGenerator"));
        config.setStringOutputType(getProperty(e, "jasypt.encryptor.stringOutputType", "base64"));

        encryptor.setConfig(config);
        return encryptor;
    }

    /**
     * 读配置并兜底默认值（未命中记 INFO 日志说明降级，便于排查参数拼写错误）。
     *
     * @param environment 环境
     * @param key         配置键（支持原样/驼峰/中划线变体查找）
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    private static String getProperty(Environment environment, String key, String defaultValue) {

        return getProperty(environment, key).orElseGet(() -> {
            log.info("Encryptor config not found for property {}, using default value: {}", key, defaultValue);
            return defaultValue;
        });
    }

    /**
     * 获取配置值
     * @param environment 环境
     * @param key 配置名
     * @return 返回配置值
     */
    private static Optional<String> getProperty(Environment environment, String key) {

        String val = environment.getProperty(key);
        if(val == null) {
            val = environment.getProperty(StrUtil.toCamelCase(key));
            if(val == null) {
                val = environment.getProperty(StrUtil.toSymbolCase(key,'-'));
            }
        }
        return Optional.ofNullable(val);
    }

    /**
     * 读必填配置，缺失抛 IllegalStateException（fail-fast，不静默降级）。
     */
    private static String getRequiredProperty(Environment environment, String key) {
        return getProperty(environment, key).orElseThrow(() -> new IllegalStateException(String.format("Required Encryption configuration property missing: %s", key)));
    }

    /**
     * 获取加密口令（jasypt.encryptor.password > jasypt.encryptor.jep），未配置 fail-fast。
     * 注意 yml 中 ${JASYPT_KEY:} 未注入时会解析为空串而非缺失，故按 isBlank 判定。
     * @param environment 环境
     * @return 加密口令
     */
    private static String getRequiredPassword(Environment environment) {
        String password = getProperty(environment, PASSWORD_PROPERTY_KEY)
                .orElseGet(() -> getProperty(environment, JEP_PROPERTY_KEY).orElse(null));
        if (StrUtil.isBlank(password)) {
            throw new IllegalStateException(String.format(
                    "Required jasypt encryption password not configured: %s or %s (via JASYPT_KEY environment variable or application-local.yml)",
                    PASSWORD_PROPERTY_KEY, JEP_PROPERTY_KEY));
        }
        return password;
    }

    /** 加密明文（懒加载初始化底层加密器后委托）。 */
    @Override
    public String encrypt(String message) {
        return singleton.get().encrypt(message);
    }

    /** 解密密文（懒加载初始化底层加密器后委托）。 */
    @Override
    public String decrypt(String encryptedMessage) {
        return singleton.get().decrypt(encryptedMessage);
    }
}
