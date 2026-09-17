package io.github.jevonsnotes.jevokit.desensitization.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import io.github.jevonsnotes.jevokit.desensitization.api.DesensitizeApi;
import io.github.jevonsnotes.jevokit.desensitization.aspect.DesensitizationAspect;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstAlgorithm;
import io.github.jevonsnotes.jevokit.desensitization.helper.DesHelper;
import io.github.jevonsnotes.jevokit.desensitization.interceptor.SensitizationPowerInterceptor;
import io.github.jevonsnotes.jevokit.desensitization.interceptor.impl.DefaultSensitizationPowerInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JevokitDesensitizationConfiguration 装配：真实 autoconfig 路线（RefreshScope 需要
 * spring-cloud RefreshAutoConfiguration 配合 ApplicationContextRunner）。
 */
class JevokitDesensitizationConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RefreshAutoConfiguration.class,
                    ConfigurationPropertiesAutoConfiguration.class,
                    JevokitDesensitizationConfiguration.class));

    @Test
    @DisplayName("默认：全部 desensitization bean 装配")
    void beansExistByDefault() {
        runner.run(context -> {
            assertThat(context).as("应装配 DesensitizationAspect").hasSingleBean(DesensitizationAspect.class);
            assertThat(context).as("应装配 DesHelper").hasSingleBean(DesHelper.class);
            // properties 为 @RefreshScope 代理（scopedTarget.* + 代理两个定义），按类型可获取即视为装配成功
            assertThat(context.getBean(JevokitDesensitizationProperties.class))
                    .as("应可按类型获取 JevokitDesensitizationProperties")
                    .isNotNull();
            assertThat(context).as("应装配默认权限拦截器")
                    .hasSingleBean(DefaultSensitizationPowerInterceptor.class);
            assertThat(context).as("应装配 DesensitizeApi").hasSingleBean(DesensitizeApi.class);
        });
    }

    @Test
    @DisplayName("spkit.desensitization.* 配置经 @ConfigurationProperties 绑定生效")
    void propertiesBoundFromEnvironment() {
        runner.withPropertyValues(
                        "spkit.desensitization.algorithm.view=SM4",
                        "spkit.desensitization.algorithm.log=MIXED",
                        "spkit.desensitization.power-check-period=ENCRYPT",
                        "spkit.desensitization.ignore-pattern[0]=secretField",
                        "spkit.desensitization.prop-pattern.lv0.mobile[0]=mobile",
                        "spkit.desensitization.prop-pattern.lv0.mobile[1]=phone")
                .run(context -> {
                    JevokitDesensitizationProperties properties =
                            context.getBean(JevokitDesensitizationProperties.class);
                    assertThat(properties.getAlgorithm().getView()).as("view 算法绑定").isEqualTo(DstAlgorithm.SM4);
                    assertThat(properties.getAlgorithm().getLog()).as("log 算法绑定").isEqualTo(DstAlgorithm.MIXED);
                    assertThat(properties.getPowerCheckPeriod().name()).as("权限检查时机绑定").isEqualTo("ENCRYPT");
                    assertThat(properties.getIgnorePattern()).as("ignore-pattern 绑定").containsExactly("secretField");
                    assertThat(properties.getPropPattern()
                            .get(io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel.LV0)
                            .get(io.github.jevonsnotes.jevokit.desensitization.consts.DstType.MOBILE))
                            .as("prop-pattern（Map<DstLevel,Map<DstType,List>>) 绑定")
                            .containsExactly("mobile", "phone");
                });
    }

    @Test
    @DisplayName("用户自定义 SensitizationPowerInterceptor 优先（@ConditionalOnMissingBean 让位）")
    void customPowerInterceptorWins() {
        runner.withBean("customInterceptor", SensitizationPowerInterceptor.class,
                        () -> (level, powerKey) -> true)
                .run(context -> {
                    assertThat(context).as("自定义拦截器应唯一存在").hasSingleBean(SensitizationPowerInterceptor.class);
                    assertThat(context.getBean(SensitizationPowerInterceptor.class)
                            .check(io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel.LV0, null))
                            .as("生效的应为用户实现（恒 true）")
                            .isTrue();
                });
    }
}
