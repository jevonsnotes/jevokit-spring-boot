package io.github.jevonsnotes.jevokit.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import io.github.jevonsnotes.jevokit.api.advice.DecryptRequestAdviceAdapter;
import io.github.jevonsnotes.jevokit.api.advice.EncryptResponseAdviceAdapter;
import io.github.jevonsnotes.jevokit.api.service.IPreventReplayAttack;
import io.github.jevonsnotes.jevokit.api.service.impl.DefaultPreventReplayAttack;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JevokitApiConfiguration 装配测试（ApplicationContextRunner，starter 惯例）。
 *
 * <p>真实 autoconfig 路线：properties bean 带 @RefreshScope，需 spring-cloud
 * RefreshAutoConfiguration 配合；类级 @ConfigurationProperties 绑定需
 * ConfigurationPropertiesAutoConfiguration。
 */
class JevokitApiConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RefreshAutoConfiguration.class,
                    ConfigurationPropertiesAutoConfiguration.class,
                    JevokitApiConfiguration.class));

    @Test
    @DisplayName("默认：api 管线全部 bean 装配（拦截器注册器/出入站 advice/客户端工具/默认防重放）")
    void beansExistByDefault() {
        runner.run(context -> {
            assertThat(context).as("应装配 WebMvcConfigurer（访问令牌拦截器注册）")
                    .hasSingleBean(JevokitApiWebMvcConfigurer.class);
            assertThat(context).as("应装配请求解密 advice").hasSingleBean(DecryptRequestAdviceAdapter.class);
            assertThat(context).as("应装配响应加密 advice").hasSingleBean(EncryptResponseAdviceAdapter.class);
            assertThat(context).as("应装配 JSecureApiUtil").hasSingleBean(JSecureApiUtil.class);
            assertThat(context).as("未自定义时应装配默认防重放实现")
                    .hasSingleBean(DefaultPreventReplayAttack.class);
            // properties 为 @RefreshScope 代理，按类型可获取即视为装配成功
            assertThat(context.getBean(JevokitApiProperties.class))
                    .as("应可按类型获取 JevokitApiProperties")
                    .isNotNull();
        });
    }

    @Test
    @DisplayName("jevokit.api.* 配置绑定生效（access 门 + client 列表）")
    void propertiesBoundFromEnvironment() {
        runner.withPropertyValues(
                        "jevokit.api.access.header=X-Token",
                        "jevokit.api.access.tokens[0].path=/demo/**",
                        "jevokit.api.access.tokens[0].token=demo-token",
                        "jevokit.api.client[0].id=runner-client",
                        "jevokit.api.client[0].secret-key=0123456789abcdef",
                        "jevokit.api.client[0].verify=true")
                .run(context -> {
                    JevokitApiProperties properties =
                            context.getBean(JevokitApiProperties.class);
                    assertThat(properties.getAccess().getHeader())
                            .as("access.header 绑定").isEqualTo("X-Token");
                    assertThat(properties.getClient())
                            .as("client 列表绑定且元素齐全").hasSize(1);
                    assertThat(properties.getClient().get(0).getId())
                            .as("client[0].id 绑定").isEqualTo("runner-client");
                    assertThat(properties.getClient().get(0).isVerify())
                            .as("client[0].verify 绑定").isTrue();
                });
    }

    @Test
    @DisplayName("用户自定义 IPreventReplayAttack 优先（@ConditionalOnMissingBean 让位）")
    void customReplayAttackWins() {
        runner.withBean("customReplay", IPreventReplayAttack.class, () -> (nonce, timestamp) -> false)
                .run(context -> {
                    assertThat(context).as("接口实现应唯一存在")
                            .hasSingleBean(IPreventReplayAttack.class);
                    assertThat(context).as("默认实现应让位")
                            .doesNotHaveBean(DefaultPreventReplayAttack.class);
                    assertThat(context.getBean(IPreventReplayAttack.class).checkReplay("n", "0"))
                            .as("生效的应为用户实现（恒 false）")
                            .isFalse();
                });
    }
}
