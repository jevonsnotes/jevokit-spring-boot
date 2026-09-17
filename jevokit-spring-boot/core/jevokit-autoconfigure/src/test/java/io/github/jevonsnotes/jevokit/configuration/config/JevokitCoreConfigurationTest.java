package io.github.jevonsnotes.jevokit.configuration.config;

import cn.hutool.crypto.asymmetric.RSA;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.jevonsnotes.jevokit.configuration.crypto.CryptoProperties;
import io.github.jevonsnotes.jevokit.core.chain.StringPropertiesSupportChain;
import io.github.jevonsnotes.jevokit.core.editor.StringPropertiesEditor;
import io.github.jevonsnotes.jevokit.core.util.SecurityUtil;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JevokitCoreConfiguration} 装配测试（ApplicationContextRunner，starter 项目惯例）：
 * crypto 属性绑定 → {@link SecurityUtil} 注入链路、无绑定时快速失败、宽松键名绑定、
 * SpringUtil 的 ConditionalOnMissingBean 回退、StringPropertiesEditor 装配。
 *
 * <p>SecurityUtil 为静态有状态：每条用例前后反射重置密钥字段，
 * 避免"先跑绑定用例再跑未绑定用例"时被污染（上下文关闭不会回收已注入的静态密钥）。
 */
@DisplayName("JevokitCoreConfiguration 装配")
class JevokitCoreConfigurationTest {

    private static final String AES_KEY = "k8tG-0123456789a";
    private static final String SM4_KEY = "sm4-0123456789ab";
    private static final String PLAIN = " hello 世界!@# ";

    private static final String RSA_PUBLIC_KEY;
    private static final String RSA_PRIVATE_KEY;

    static {
        RSA rsa = new RSA();
        RSA_PUBLIC_KEY = rsa.getPublicKeyBase64();
        RSA_PRIVATE_KEY = rsa.getPrivateKeyBase64();
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JevokitCoreConfiguration.class))
            .withBean("upperChain", UpperChain.class);

    /** 测试用编辑链：set 方向加 "-set" 后缀，get 方向加 "-get" 后缀 */
    static class UpperChain implements StringPropertiesSupportChain {
        @Override
        public String getTextEdit(String text) {
            return text + "-get";
        }

        @Override
        public String setTextEdit(String text) {
            return text + "-set";
        }
    }

    @BeforeEach
    void resetKeys() {
        resetSecurityUtilKeys();
    }

    @AfterEach
    void cleanUp() {
        resetSecurityUtilKeys();
    }

    @Test
    @DisplayName("装配基础：三个 Bean 均注册，CryptoProperties 未配置时字段为 null")
    void registersCoreBeans() {
        runner.run(context -> {
            // then
            assertThat(context).as("上下文应正常启动").hasNotFailed();
            assertThat(context).as("应注册 StringPropertiesEditor")
                    .hasSingleBean(StringPropertiesEditor.class);
            assertThat(context).as("应注册 SpringUtil")
                    .hasSingleBean(cn.hutool.extra.spring.SpringUtil.class);
            assertThat(context).as("应注册 cryptoKeyInitializer")
                    .hasBean("cryptoKeyInitializer");
            CryptoProperties props = context.getBean(CryptoProperties.class);
            assertThat(props.getAesKey()).as("未配置时 aes-key 应为 null").isNull();
            assertThat(props.getSm4Key()).as("未配置时 sm4-key 应为 null").isNull();
        });
    }

    @Test
    @DisplayName("crypto 链路：绑定 spkit.crypto 四键后 SecurityUtil 无参方法全部可用")
    void cryptoKeysInjectedIntoSecurityUtil() {
        runner.withPropertyValues(
                        "spkit.crypto.rsa-public-key=" + RSA_PUBLIC_KEY,
                        "spkit.crypto.rsa-private-key=" + RSA_PRIVATE_KEY,
                        "spkit.crypto.aes-key=" + AES_KEY,
                        "spkit.crypto.sm4-key=" + SM4_KEY)
                .run(context -> {
                    // given：上下文启动完成时 cryptoKeyInitializer（InitializingBean）已执行
                    assertThat(context).hasNotFailed();
                    // when / then：四算法无参往返
                    assertThat(SecurityUtil.aesDecrypt(SecurityUtil.aesEncrypt(PLAIN)))
                            .as("aes 无参方法应经注入密钥工作").isEqualTo(PLAIN);
                    assertThat(SecurityUtil.sm4cbcDecrypt(SecurityUtil.sm4cbcEncrypt(PLAIN)))
                            .as("sm4 无参方法应经注入密钥工作").isEqualTo(PLAIN);
                    assertThat(SecurityUtil.rsaDecrypt(SecurityUtil.rsaEncrypt(PLAIN)))
                            .as("rsa 无参方法应经注入密钥工作").isEqualTo(PLAIN);
                });
    }

    @Test
    @DisplayName("crypto 链路：不绑定任何配置时无参方法抛 IllegalStateException 并指明配置键")
    void withoutCryptoConfigSecurityUtilFailsFast() {
        runner.run(context -> {
            // given：上下文正常启动（InitializingBean 对全 null 配置是安全的）
            assertThat(context).hasNotFailed();
            // then
            assertThatThrownBy(() -> SecurityUtil.aesEncrypt(PLAIN))
                    .as("未绑定 spkit.crypto.aes-key 时应快速失败")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("spkit.crypto.aes-key");
            assertThatThrownBy(() -> SecurityUtil.sm4cbcEncrypt(PLAIN))
                    .as("未绑定 spkit.crypto.sm4-key 时应快速失败")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("spkit.crypto.sm4-key");
            assertThatThrownBy(() -> SecurityUtil.rsaEncrypt(PLAIN))
                    .as("未绑定 spkit.crypto.rsa-private-key 时应快速失败")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("spkit.crypto.rsa-private-key");
        });
    }

    @Test
    @DisplayName("crypto 链路：部分绑定时各算法独立生效")
    void partialCryptoConfigOnlyEnablesBoundAlgorithms() {
        runner.withPropertyValues(
                        "spkit.crypto.aes-key=" + AES_KEY)
                .run(context -> {
                    // then：aes 可用
                    assertThat(SecurityUtil.aesDecrypt(SecurityUtil.aesEncrypt(PLAIN)))
                            .as("仅绑定 aes-key 时 aes 应可用").isEqualTo(PLAIN);
                    // and：sm4 仍快速失败
                    assertThatThrownBy(() -> SecurityUtil.sm4cbcEncrypt(PLAIN))
                            .as("未绑定的 sm4 应保持快速失败")
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("spkit.crypto.sm4-key");
                });
    }

    @Test
    @DisplayName("宽松键名绑定：camelCase 键名回退到 kebab-case 规范属性")
    void relaxedCamelCaseKeyBinding() {
        runner.withPropertyValues(
                        "spkit.crypto.rsaPublicKey=" + RSA_PUBLIC_KEY,
                        "spkit.crypto.rsaPrivateKey=" + RSA_PRIVATE_KEY)
                .run(context -> {
                    // then：camelCase 源键应绑定到 kebab 规范属性（Spring Boot relaxed binding）
                    CryptoProperties props = context.getBean(CryptoProperties.class);
                    assertThat(props.getRsaPublicKey()).as("camelCase 公钥键应完成绑定").isEqualTo(RSA_PUBLIC_KEY);
                    assertThat(props.getRsaPrivateKey()).as("camelCase 私钥键应完成绑定").isEqualTo(RSA_PRIVATE_KEY);
                    // and：注入链路同样生效
                    assertThat(SecurityUtil.rsaDecrypt(SecurityUtil.rsaEncrypt(PLAIN)))
                            .as("宽松键名绑定的密钥应可正常加解密").isEqualTo(PLAIN);
                });
    }

    @Test
    @DisplayName("springUtil：用户自定义同名 Bean 时 ConditionalOnMissingBean 回退")
    void springUtilBacksOffWhenUserDefinesOwn() {
        // given：用户自定义配置提供自己的 SpringUtil
        new ApplicationContextRunner()
                .withUserConfiguration(UserSpringUtilConfig.class)
                .withConfiguration(AutoConfigurations.of(JevokitCoreConfiguration.class))
                .run(context -> {
                    // then：仅一个 SpringUtil，且是用户那个
                    assertThat(context.getBeansOfType(cn.hutool.extra.spring.SpringUtil.class))
                            .as("用户自定义时应回退，只保留一个 SpringUtil").hasSize(1);
                    assertThat(context.getBean("userSpringUtil"))
                            .as("保留的应是用户定义的 Bean").isSameAs(context.getBean(cn.hutool.extra.spring.SpringUtil.class));
                });
    }

    @Configuration
    static class UserSpringUtilConfig {
        @Bean
        public cn.hutool.extra.spring.SpringUtil userSpringUtil() {
            return new cn.hutool.extra.spring.SpringUtil();
        }
    }

    @Test
    @DisplayName("stringPropertiesEditor：注册的编辑链在 set/get 两个方向生效")
    void stringPropertiesEditorAppliesChains() {
        runner.run(context -> {
            // given
            StringPropertiesEditor editor = context.getBean(StringPropertiesEditor.class);
            // when
            editor.setAsText("form-value");
            Object setValue = editor.getValue();
            editor.setValue("model-value");
            String getText = editor.getAsText();
            // then：set 方向过 setTextEdit 链，get 方向过 getTextEdit 链
            assertThat(setValue).as("setAsText 应依次经过全部 chain 的 setTextEdit").isEqualTo("form-value-set");
            assertThat(getText).as("getAsText 应依次经过全部 chain 的 getTextEdit").isEqualTo("model-value-get");
        });
    }

    /** 反射重置 SecurityUtil 静态密钥字段（上下文关闭不会回收已注入密钥） */
    private static void resetSecurityUtilKeys() {
        for (String fieldName : new String[]{"rsaPublicKey", "rsaPrivateKey", "aesKey", "sm4Key"}) {
            try {
                Field field = SecurityUtil.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(null, null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("重置 SecurityUtil 静态字段失败：" + fieldName, e);
            }
        }
    }
}
