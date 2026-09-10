package springbootkit.test.server;

import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

/**
 * jasypt 加解密工具样例。
 * 密钥经环境变量 JASYPT_KEY 注入（与 application.yml 的 ${JASYPT_KEY:} 一致），
 * 算法与 IV 生成器镜像运行时 SpringBootKitStringEncryptor 默认配置
 * （PBEWithMD5AndDES + NoIvGenerator），产物可直接用作配置文件中的 jasypt 密文。
 */
public class JasyptTest {

    public static void main(String[] args) {
        String password = System.getenv("JASYPT_KEY");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("环境变量 JASYPT_KEY 未配置");
        }
        String algorithm = "PBEWithMD5AndDES";
        String input = "test-plain-text";

        String encrypt = encrypt(input, password, algorithm);
        System.out.println(encrypt);
        String decrypt = decrypt(encrypt, password, algorithm);
        System.out.println(decrypt);
    }

    public static String encrypt(String secret, String password, String algorithm) {
        return getEncryptor(password, algorithm).encrypt(secret);
    }

    public static String decrypt(String secret, String password, String algorithm) {
        return getEncryptor(password, algorithm).decrypt(secret);
    }

    /**
     * 与运行时默认加密器一致的配置
     *
     * @param password  密钥
     * @param algorithm 算法
     * @return Encryptor
     */
    public static PooledPBEStringEncryptor getEncryptor(String password, String algorithm) {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(password);
        config.setAlgorithm(algorithm);
        config.setKeyObtentionIterations("1000");
        config.setPoolSize("1");
        config.setProviderName("SunJCE");
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.iv.NoIvGenerator");
        config.setStringOutputType("base64");
        encryptor.setConfig(config);
        return encryptor;
    }
}
