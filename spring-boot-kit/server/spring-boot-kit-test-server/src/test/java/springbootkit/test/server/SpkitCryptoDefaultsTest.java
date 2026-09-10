package springbootkit.test.server;

import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import springbootkit.configuration.crypto.CryptoProperties;
import springbootkit.core.util.SecurityUtil;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * 验证 application.yml 中 spkit.crypto.* 演示默认密钥（demo 应用开箱即用值）：
 * <ol>
 *   <li>能被 Spring 配置绑定机制绑定到 {@link CryptoProperties}（Binder，与 @ConfigurationProperties 同机制）；</li>
 *   <li>{@link SecurityUtil} 无参加解密方法使用演示密钥不抛 IllegalStateException，且密文可往返还原。</li>
 * </ol>
 * 不启动完整应用上下文（避免依赖 Redis/RocketMQ 等外部环境），仅验证配置绑定与密钥有效性。
 */
public class SpkitCryptoDefaultsTest {

    private static CryptoProperties properties;

    @BeforeClass
    public static void bindAndInit() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        Binder binder = new Binder(ConfigurationPropertySources.from(sources));
        properties = binder.bind("spkit.crypto", Bindable.of(CryptoProperties.class)).get();
        SecurityUtil.init(properties.getRsaPublicKey(), properties.getRsaPrivateKey(),
                properties.getAesKey(), properties.getSm4Key());
    }

    @Test
    public void cryptoDefaultsBoundFromYaml() {
        assertNotNull("spkit.crypto.rsa-public-key 未绑定", properties.getRsaPublicKey());
        assertNotNull("spkit.crypto.rsa-private-key 未绑定", properties.getRsaPrivateKey());
        assertEquals("aes-key 应为 16 字节演示密钥", 16, properties.getAesKey().getBytes().length);
        assertEquals("sm4-key 应为 16 字节演示密钥", 16, properties.getSm4Key().getBytes().length);
    }

    @Test
    public void aesRoundTripWithDefaults() {
        String plain = "spkit-demo-plain";
        assertEquals(plain, SecurityUtil.aesDecrypt(SecurityUtil.aesEncrypt(plain)));
    }

    @Test
    public void sm4RoundTripWithDefaults() {
        String plain = "spkit-demo-plain";
        assertEquals(plain, SecurityUtil.sm4cbcDecrypt(SecurityUtil.sm4cbcEncrypt(plain)));
    }

    @Test
    public void rsaRoundTripWithDefaults() {
        // 无参用法方向：私钥加密 → 公钥解密；往返成功证明 yml 中演示密钥对真实配对
        String plain = "spkit-demo-plain";
        assertEquals(plain, SecurityUtil.rsaDecrypt(SecurityUtil.rsaEncrypt(plain)));
    }
}
