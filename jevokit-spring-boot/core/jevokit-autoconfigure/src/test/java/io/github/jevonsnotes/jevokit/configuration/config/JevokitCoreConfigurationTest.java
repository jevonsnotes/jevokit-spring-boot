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
 * crypto 属性绑定 → {@link SecurityUtil} 注入链路、startup-check 启动校验（默认拦缺失键/关闭后延迟失败）、
 * 宽松键名绑定、SpringUtil 的 ConditionalOnMissingBean 回退、StringPropertiesEditor 装配。
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
    @DisplayName("装配基础：三个 Bean 均注册；startup-check=false 时未配置密钥不拦启动（字段为 null）")
    void registersCoreBeans() {
        runner.withPropertyValues("jevokit.crypto.startup-check=false").run(context -> {
            // then
            assertThat(context).as("startup-check 关闭时上下文应正常启动").hasNotFailed();
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
    @DisplayName("crypto 链路：绑定 jevokit.crypto 四键后 SecurityUtil 无参方法全部可用")
    void cryptoKeysInjectedIntoSecurityUtil() {
        runner.withPropertyValues(
                        "jevokit.crypto.rsa-public-key=" + RSA_PUBLIC_KEY,
                        "jevokit.crypto.rsa-private-key=" + RSA_PRIVATE_KEY,
                        "jevokit.crypto.aes-key=" + AES_KEY,
                        "jevokit.crypto.sm4-key=" + SM4_KEY)
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
    @DisplayName("crypto 链路：startup-check=false 时不拦启动，无参方法抛 ISE 并指明配置键（延迟失败）")
    void withoutCryptoConfigSecurityUtilFailsFast() {
        runner.withPropertyValues("jevokit.crypto.startup-check=false").run(context -> {
            // given：关闭启动校验后上下文正常启动（与开关引入前的现状一致）
            assertThat(context).hasNotFailed();
            // then
            assertThatThrownBy(() -> SecurityUtil.aesEncrypt(PLAIN))
                    .as("未绑定 jevokit.crypto.aes-key 时应快速失败")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jevokit.crypto.aes-key");
            assertThatThrownBy(() -> SecurityUtil.sm4cbcEncrypt(PLAIN))
                    .as("未绑定 jevokit.crypto.sm4-key 时应快速失败")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jevokit.crypto.sm4-key");
            assertThatThrownBy(() -> SecurityUtil.rsaEncrypt(PLAIN))
                    .as("未绑定 jevokit.crypto.rsa-private-key 时应快速失败")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jevokit.crypto.rsa-private-key");
        });
    }

    @Test
    @DisplayName("crypto 链路：startup-check=false 下部分绑定时各算法独立生效")
    void partialCryptoConfigOnlyEnablesBoundAlgorithms() {
        runner.withPropertyValues(
                        "jevokit.crypto.startup-check=false",
                        "jevokit.crypto.aes-key=" + AES_KEY)
                .run(context -> {
                    // then：aes 可用
                    assertThat(SecurityUtil.aesDecrypt(SecurityUtil.aesEncrypt(PLAIN)))
                            .as("仅绑定 aes-key 时 aes 应可用").isEqualTo(PLAIN);
                    // and：sm4 仍快速失败
                    assertThatThrownBy(() -> SecurityUtil.sm4cbcEncrypt(PLAIN))
                            .as("未绑定的 sm4 应保持快速失败")
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("jevokit.crypto.sm4-key");
                });
    }

    @Test
    @DisplayName("宽松键名绑定：camelCase 键名回退到 kebab-case 规范属性")
    void relaxedCamelCaseKeyBinding() {
        runner.withPropertyValues(
                        "jevokit.crypto.startup-check=false",
                        "jevokit.crypto.rsaPublicKey=" + RSA_PUBLIC_KEY,
                        "jevokit.crypto.rsaPrivateKey=" + RSA_PRIVATE_KEY)
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

    // ------------------------- startup-check 启动校验 -------------------------

    @Test
    @DisplayName("startup-check 默认开启：四密钥全缺 → 启动失败，消息一次性列出全部缺失键与关闭方式")
    void startupCheckFailsWhenAllKeysMissing() {
        runner.run(context -> {
            // then：默认 startup-check=true，无任何密钥 → 启动失败
            assertThat(context).as("缺全部密钥时默认应启动失败").hasFailed();
            String trace = stackTraceOf(context.getStartupFailure());
            assertThat(context.getStartupFailure())
                    .as("根因应为启动校验的 IllegalStateException")
                    .hasRootCauseInstanceOf(IllegalStateException.class);
            // 异常消息含全部四个缺失键（一次报全，不挤牙膏）并提示关闭方式
            assertThat(trace)
                    .contains("jevokit.crypto.rsa-public-key")
                    .contains("jevokit.crypto.rsa-private-key")
                    .contains("jevokit.crypto.aes-key")
                    .contains("jevokit.crypto.sm4-key")
                    .contains("jevokit.crypto.startup-check=false");
        });
    }

    @Test
    @DisplayName("startup-check 部分缺键：消息只列缺失的键，已配置的键不出现")
    void startupCheckListsOnlyMissingKeys() {
        runner.withPropertyValues(
                        "jevokit.crypto.rsa-public-key=" + RSA_PUBLIC_KEY,
                        "jevokit.crypto.rsa-private-key=" + RSA_PRIVATE_KEY)
                .run(context -> {
                    // then
                    assertThat(context).as("部分缺键时默认应启动失败").hasFailed();
                    assertThat(context.getStartupFailure())
                            .as("根因应为启动校验的 IllegalStateException")
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(stackTraceOf(context.getStartupFailure()))
                            .as("缺失的 aes/sm4 键应一次报全，已配置的 rsa 键不出现在缺失清单")
                            .contains("jevokit.crypto.aes-key", "jevokit.crypto.sm4-key")
                            .doesNotContain("rsa-public-key", "rsa-private-key");
                });
    }

    @Test
    @DisplayName("startup-check=false：缺密钥不拦启动，保持延迟失败（现状行为）")
    void startupCheckDisabledSkipsStartupFailure() {
        runner.withPropertyValues("jevokit.crypto.startup-check=false").run(context -> {
            // then：开关关闭，任一密钥缺失都不拦启动
            assertThat(context).as("关闭校验后应正常启动").hasNotFailed();
            // and：失败延迟到首次使用（requireKey 语义）
            assertThatThrownBy(() -> SecurityUtil.sm4cbcEncrypt(PLAIN))
                    .as("延迟失败应与开关引入前一致")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jevokit.crypto.sm4-key");
        });
    }

    @Test
    @DisplayName("startup-check 开启且四密钥齐全：启动通过（cryptoKeysInjectedIntoSecurityUtil 同证）")
    void startupCheckPassesWithAllKeysConfigured() {
        runner.withPropertyValues(
                        "jevokit.crypto.rsa-public-key=" + RSA_PUBLIC_KEY,
                        "jevokit.crypto.rsa-private-key=" + RSA_PRIVATE_KEY,
                        "jevokit.crypto.aes-key=" + AES_KEY,
                        "jevokit.crypto.sm4-key=" + SM4_KEY)
                .run(context -> assertThat(context).as("四密钥齐全时校验应通过").hasNotFailed());
    }

    @Test
    @DisplayName("springUtil：用户自定义同名 Bean 时 ConditionalOnMissingBean 回退")
    void springUtilBacksOffWhenUserDefinesOwn() {
        // given：用户自定义配置提供自己的 SpringUtil
        new ApplicationContextRunner()
                .withPropertyValues("jevokit.crypto.startup-check=false")
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
        runner.withPropertyValues("jevokit.crypto.startup-check=false").run(context -> {
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

    /** 启动失败异常的完整栈文本（含 cause 链消息）——InitializingBean 抛出的 ISE 会被 Spring 包一层 */
    private static String stackTraceOf(Throwable failure) {
        java.io.StringWriter sw = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
