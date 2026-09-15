package springbootkit.security.code.inject.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import springbootkit.security.code.inject.util.CodeInjectUtil;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CodeInjectConfiguration 装配条件：spkit.security.code-inject.enabled 开关切换 bean 存在性；
 * 并验证 @Autowired initProperties 在容器内完成静态规则注入。
 */
class CodeInjectConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    // @RefreshScope 的 properties 绑定需要 refresh scope 支持（spring-cloud-context）
                    org.springframework.cloud.autoconfigure.RefreshAutoConfiguration.class,
                    ConfigurationPropertiesAutoConfiguration.class,
                    CodeInjectConfiguration.class));

    @Test
    @DisplayName("默认（无配置，matchIfMissing=true）：CodeInjectUtil bean 装配")
    void beanExistsByDefault() {
        runner.run(context ->
                assertThat(context).as("默认应装配 CodeInjectUtil").hasSingleBean(CodeInjectUtil.class));
    }

    @Test
    @DisplayName("spkit.security.code-inject.enabled=true：bean 装配")
    void beanExistsWhenEnabled() {
        runner.withPropertyValues("spkit.security.code-inject.enabled=true").run(context ->
                assertThat(context).as("显式开启应装配 CodeInjectUtil").hasSingleBean(CodeInjectUtil.class));
    }

    @Test
    @DisplayName("spkit.security.code-inject.enabled=false：bean 不装配")
    void beanAbsentWhenDisabled() {
        runner.withPropertyValues("spkit.security.code-inject.enabled=false").run(context ->
                assertThat(context).as("关闭后不得装配 CodeInjectUtil").doesNotHaveBean(CodeInjectUtil.class));
    }

    @Test
    @DisplayName("容器内注入 spkit.security.code-inject.namespaces 后 isSafe 生效")
    void namespacesBoundAndEffective() {
        runner.withPropertyValues(
                        "spkit.security.code-inject.namespaces.default[0]=[a-z0-9_]+")
                .run(context -> {
                    assertThat(context).hasSingleBean(CodeInjectUtil.class);
                    assertThat(CodeInjectUtil.isSafe("order_no_1"))
                            .as("容器装配后静态规则应生效：白名单值安全")
                            .isTrue();
                    assertThat(CodeInjectUtil.isSafe("alert(1)"))
                            .as("容器装配后静态规则应生效：非白名单值不安全")
                            .isFalse();
                });
    }
}
