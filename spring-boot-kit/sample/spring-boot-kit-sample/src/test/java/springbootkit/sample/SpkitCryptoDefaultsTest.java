package springbootkit.sample;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import springbootkit.configuration.crypto.CryptoProperties;
import springbootkit.core.util.SecurityUtil;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 application.yml 中 spkit.crypto.* 演示默认密钥（demo 应用开箱即用值）：
 * <ol>
 *   <li>能被 Spring 配置绑定机制绑定到 {@link CryptoProperties}（Binder，与 @ConfigurationProperties 同机制）；</li>
 *   <li>{@link SecurityUtil} 无参加解密方法使用演示密钥不抛 IllegalStateException，且密文可往返还原。</li>
 * </ol>
 * 不启动完整应用上下文（避免依赖 Redis/RocketMQ 等外部环境），仅验证配置绑定与密钥有效性。
 *
 * <p>（终验 #6 自 JUnit4 迁 JUnit5 + AssertJ，断言语义逐条保留。）
 */
@DisplayName("spkit.crypto 演示默认密钥绑定与可用性防线")
class SpkitCryptoDefaultsTest {

    private static CryptoProperties properties;

    @BeforeAll
    static void bindAndInit() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        Binder binder = new Binder(ConfigurationPropertySources.from(sources));
        properties = binder.bind("spkit.crypto", Bindable.of(CryptoProperties.class)).get();
        SecurityUtil.init(properties.getRsaPublicKey(), properties.getRsaPrivateKey(),
                properties.getAesKey(), properties.getSm4Key());
    }

    @Test
    @DisplayName("yml 演示密钥经 Spring Binder 绑定成功且长度合规")
    void cryptoDefaultsBoundFromYaml() {
        assertThat(properties.getRsaPublicKey()).as("spkit.crypto.rsa-public-key 未绑定").isNotNull();
        assertThat(properties.getRsaPrivateKey()).as("spkit.crypto.rsa-private-key 未绑定").isNotNull();
        assertThat(properties.getAesKey().getBytes())
                .as("aes-key 应为 16 字节演示密钥")
                .hasSize(16);
        assertThat(properties.getSm4Key().getBytes())
                .as("sm4-key 应为 16 字节演示密钥")
                .hasSize(16);
    }

    @Test
    @DisplayName("演示密钥 AES 往返还原")
    void aesRoundTripWithDefaults() {
        String plain = "spkit-demo-plain";
        assertThat(SecurityUtil.aesDecrypt(SecurityUtil.aesEncrypt(plain)))
                .as("AES 演示密钥往返应还原明文")
                .isEqualTo(plain);
    }

    @Test
    @DisplayName("演示密钥 SM4-CBC 往返还原")
    void sm4RoundTripWithDefaults() {
        String plain = "spkit-demo-plain";
        assertThat(SecurityUtil.sm4cbcDecrypt(SecurityUtil.sm4cbcEncrypt(plain)))
                .as("SM4 演示密钥往返应还原明文")
                .isEqualTo(plain);
    }

    @Test
    @DisplayName("演示密钥 RSA 往返还原（私钥加密→公钥解密，证明密钥对真实配对）")
    void rsaRoundTripWithDefaults() {
        // 无参用法方向：私钥加密 → 公钥解密；往返成功证明 yml 中演示密钥对真实配对
        String plain = "spkit-demo-plain";
        assertThat(SecurityUtil.rsaDecrypt(SecurityUtil.rsaEncrypt(plain)))
                .as("RSA 演示密钥对往返应还原明文")
                .isEqualTo(plain);
    }
}
