package io.github.jevonsnotes.jevokit.security.web.fixed.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import io.github.jevonsnotes.jevokit.security.web.fixed.aspect.SpringWebFixedAspect;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SpringWebFixedConfiguration 装配：无 enabled 开关（该防护默认无条件开启），
 * 与其余 security 模块的 jevokit.security.*.enabled 条件装配不同，本类固化"始终装配"语义。
 */
class SpringWebFixedConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SpringWebFixedConfiguration.class));

    @Test
    @DisplayName("默认：SpringWebFixedAspect bean 装配")
    void aspectBeanExistsByDefault() {
        runner.run(context ->
                assertThat(context).as("web-fixed 防护应默认装配").hasSingleBean(SpringWebFixedAspect.class));
    }

    @Test
    @DisplayName("无 jevokit.security.web-fixed.enabled 开关（始终装配语义固化）")
    void aspectBeanExistsRegardlessOfProperties() {
        runner.withPropertyValues("jevokit.security.web-fixed.enabled=false").run(context ->
                assertThat(context)
                        .as("该模块不提供关闭开关：即使误配 enabled=false 也保持装配（防护不被误关）")
                        .hasSingleBean(SpringWebFixedAspect.class));
    }
}
