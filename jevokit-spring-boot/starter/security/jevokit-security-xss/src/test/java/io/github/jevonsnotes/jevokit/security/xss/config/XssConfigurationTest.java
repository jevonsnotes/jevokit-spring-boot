package io.github.jevonsnotes.jevokit.security.xss.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import io.github.jevonsnotes.jevokit.configuration.config.JevokitCoreConfiguration;
import io.github.jevonsnotes.jevokit.security.xss.chain.XssStringPropertiesEditChain;
import io.github.jevonsnotes.jevokit.security.xss.cleaner.XssCleaner;
import io.github.jevonsnotes.jevokit.security.xss.handler.FormXssHandler;
import io.github.jevonsnotes.jevokit.security.xss.handler.ResponseXssHandler;
import io.github.jevonsnotes.jevokit.security.xss.handler.XmlXssHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XssConfiguration 装配条件：jevokit.security.xss.enabled 开关切换 bean 存在性（SERVLET web 环境下）。
 */
class XssConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JevokitCoreConfiguration.class,
                    ConfigurationPropertiesAutoConfiguration.class,
                    XssConfiguration.class));

    @Test
    @DisplayName("默认（无配置，matchIfMissing=true）：全部 xss bean 装配")
    void beansExistByDefault() {
        runner.run(context -> {
            assertThat(context).as("默认应装配 XssCleaner").hasSingleBean(XssCleaner.class);
            assertThat(context).as("默认应装配 FormXssHandler").hasSingleBean(FormXssHandler.class);
            assertThat(context).as("默认应装配 XssStringPropertiesEditChain").hasSingleBean(XssStringPropertiesEditChain.class);
            assertThat(context).as("默认应装配 ResponseXssHandler").hasSingleBean(ResponseXssHandler.class);
            assertThat(context).as("默认应装配 XmlXssHandler").hasSingleBean(XmlXssHandler.class);
            assertThat(context).as("默认应装配 Jackson 定制器").hasSingleBean(Jackson2ObjectMapperBuilderCustomizer.class);
        });
    }

    @Test
    @DisplayName("jevokit.security.xss.enabled=true：全部 xss bean 装配")
    void beansExistWhenEnabled() {
        runner.withPropertyValues("jevokit.security.xss.enabled=true").run(context -> {
            assertThat(context).as("显式开启应装配 XssCleaner").hasSingleBean(XssCleaner.class);
            assertThat(context).as("显式开启应装配 FormXssHandler").hasSingleBean(FormXssHandler.class);
        });
    }

    @Test
    @DisplayName("jevokit.security.xss.enabled=false：全部 xss bean 不装配")
    void beansAbsentWhenDisabled() {
        runner.withPropertyValues("jevokit.security.xss.enabled=false").run(context -> {
            assertThat(context).as("关闭后不得装配 XssCleaner").doesNotHaveBean(XssCleaner.class);
            assertThat(context).as("关闭后不得装配 FormXssHandler").doesNotHaveBean(FormXssHandler.class);
            assertThat(context).as("关闭后不得装配 XssStringPropertiesEditChain").doesNotHaveBean(XssStringPropertiesEditChain.class);
            assertThat(context).as("关闭后不得装配 ResponseXssHandler").doesNotHaveBean(ResponseXssHandler.class);
            assertThat(context).as("关闭后不得装配 XmlXssHandler").doesNotHaveBean(XmlXssHandler.class);
            assertThat(context).as("关闭后不得装配 Jackson 定制器").doesNotHaveBean(Jackson2ObjectMapperBuilderCustomizer.class);
        });
    }

    @Test
    @DisplayName("用户自定义 XssCleaner 优先（@ConditionalOnMissingBean 让位）")
    void customCleanerWins() {
        runner.withBean("customXssCleaner", XssCleaner.class, () -> content -> content)
                .run(context -> {
                    assertThat(context).as("自定义 cleaner 应唯一存在").hasSingleBean(XssCleaner.class);
                    assertThat(context.getBean(XssCleaner.class).clean("<script>x</script>"))
                            .as("容器内的 cleaner 应为用户实现（恒等）")
                            .isEqualTo("<script>x</script>");
                });
    }
}
