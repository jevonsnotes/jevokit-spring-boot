package io.github.jevonsnotes.jevokit.security.pt.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import io.github.jevonsnotes.jevokit.configuration.config.JevokitCoreConfiguration;
import io.github.jevonsnotes.jevokit.security.pt.chain.PtStringPropertiesEditChain;
import io.github.jevonsnotes.jevokit.security.pt.handler.FormPathTraversalHandler;
import io.github.jevonsnotes.jevokit.security.pt.handler.RequestBodyPathTraversalHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PathTraversalConfiguration 装配条件：jevokit.security.path-traversal.enabled 开关切换 bean 存在性。
 */
class PathTraversalConfigurationTest {

    // jevokit.crypto.startup-check 默认 true：本测试聚焦 path-traversal 装配条件，不配 crypto 密钥，关掉启动校验免拦
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withPropertyValues("jevokit.crypto.startup-check=false")
            .withConfiguration(AutoConfigurations.of(
                    JevokitCoreConfiguration.class,
                    ConfigurationPropertiesAutoConfiguration.class,
                    PathTraversalConfiguration.class));

    @Test
    @DisplayName("默认（无配置，matchIfMissing=true）：全部 path-traversal bean 装配")
    void beansExistByDefault() {
        runner.run(context -> {
            assertThat(context).as("默认应装配 RequestBodyPathTraversalHandler").hasSingleBean(RequestBodyPathTraversalHandler.class);
            assertThat(context).as("默认应装配 FormPathTraversalHandler").hasSingleBean(FormPathTraversalHandler.class);
            assertThat(context).as("默认应装配 PtStringPropertiesEditChain").hasSingleBean(PtStringPropertiesEditChain.class);
        });
    }

    @Test
    @DisplayName("jevokit.security.path-traversal.enabled=true：全部 bean 装配")
    void beansExistWhenEnabled() {
        runner.withPropertyValues("jevokit.security.path-traversal.enabled=true").run(context ->
                assertThat(context).as("显式开启应装配 RequestBodyPathTraversalHandler").hasSingleBean(RequestBodyPathTraversalHandler.class));
    }

    @Test
    @DisplayName("jevokit.security.path-traversal.enabled=false：全部 bean 不装配")
    void beansAbsentWhenDisabled() {
        runner.withPropertyValues("jevokit.security.path-traversal.enabled=false").run(context -> {
            assertThat(context).as("关闭后不得装配 RequestBodyPathTraversalHandler").doesNotHaveBean(RequestBodyPathTraversalHandler.class);
            assertThat(context).as("关闭后不得装配 FormPathTraversalHandler").doesNotHaveBean(FormPathTraversalHandler.class);
            assertThat(context).as("关闭后不得装配 PtStringPropertiesEditChain").doesNotHaveBean(PtStringPropertiesEditChain.class);
        });
    }
}
