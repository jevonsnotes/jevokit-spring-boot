package io.github.jevonsnotes.jevokit.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import io.github.jevonsnotes.jevokit.configuration.audit.log.AuditLogProperties;
import io.github.jevonsnotes.jevokit.configuration.crypto.CryptoProperties;
import io.github.jevonsnotes.jevokit.configuration.security.code.inject.CodeInjectProperties;
import io.github.jevonsnotes.jevokit.configuration.security.pathtraversal.PathTraversalProperties;
import io.github.jevonsnotes.jevokit.configuration.security.sql.inject.SqlInjectProperties;
import io.github.jevonsnotes.jevokit.configuration.security.xss.XssProperties;
import io.github.jevonsnotes.jevokit.core.consts.AuditLogPersistentMode;
import io.github.jevonsnotes.jevokit.core.consts.HttpMethod;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 各 @ConfigurationProperties 绑定用例：前缀正确性、默认值、宽松键名（camelCase 回退 kebab 规范）、
 * 内建污染正则合并（getAllTaintedPatterns）。
 *
 * <p>合并断言带阳性对照：先断言已知内建条目出现（证明合并链路活着），
 * 再断言用户新增条目——避免"零命中被当通过"（docs/lessons 检索类教训的同类思路）。
 */
@DisplayName("jevokit.* Properties 绑定")
class ConfigurationPropertiesBindingTest {

    @Configuration
    @EnableConfigurationProperties({
            CryptoProperties.class,
            AuditLogProperties.class,
            XssProperties.class,
            SqlInjectProperties.class,
            PathTraversalProperties.class,
            CodeInjectProperties.class})
    static class AllPropsConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            // 属性类带 @RefreshScope（scope "refresh"）：纯 runner 中需注册 spring-cloud 的 RefreshScope
            // （GenericScope 是 BeanFactoryPostProcessor，会自注册为同名 scope），否则上下文无法创建属性 Bean
            .withBean(org.springframework.cloud.context.scope.refresh.RefreshScope.class)
            .withUserConfiguration(AllPropsConfig.class);

    @Nested
    @DisplayName("CryptoProperties（jevokit.crypto）")
    class Crypto {

        @Test
        @DisplayName("默认不配置任何密钥（替代历史内置默认密钥的安全语义）")
        void defaultsAreNull() {
            runner.run(context -> {
                CryptoProperties props = context.getBean(CryptoProperties.class);
                assertThat(props.getRsaPublicKey()).as("rsa-public-key 默认 null").isNull();
                assertThat(props.getRsaPrivateKey()).as("rsa-private-key 默认 null").isNull();
                assertThat(props.getAesKey()).as("aes-key 默认 null").isNull();
                assertThat(props.getSm4Key()).as("sm4-key 默认 null").isNull();
            });
        }

        @Test
        @DisplayName("kebab-case 键绑定")
        void kebabBinding() {
            runner.withPropertyValues(
                            "jevokit.crypto.aes-key=aes-0123456789ab",
                            "jevokit.crypto.sm4-key=sm4-0123456789a")
                    .run(context -> {
                        CryptoProperties props = context.getBean(CryptoProperties.class);
                        assertThat(props.getAesKey()).as("aes-key 应绑定").isEqualTo("aes-0123456789ab");
                        assertThat(props.getSm4Key()).as("sm4-key 应绑定").isEqualTo("sm4-0123456789a");
                    });
        }
    }

    @Nested
    @DisplayName("AuditLogProperties（jevokit.audit.log）")
    class AuditLog {

        @Test
        @DisplayName("默认值：关闭、LOG 模式、TRUNC 策略、限长 -1")
        void defaults() {
            runner.run(context -> {
                AuditLogProperties props = context.getBean(AuditLogProperties.class);
                assertThat(props.isEnabled()).as("enabled 默认 false").isFalse();
                assertThat(props.isEnabledMdc()).as("enabledMdc 默认 false").isFalse();
                assertThat(props.isEnabledAround()).as("enabledAround 默认 false").isFalse();
                assertThat(props.isIgnoreNonUser()).as("ignoreNonUser 默认 false").isFalse();
                assertThat(props.getPersistent().getMode())
                        .as("持久化默认 LOG").isEqualTo(AuditLogPersistentMode.LOG);
                assertThat(props.getPersistent().getDbMigratePeriod().name())
                        .as("转储周期默认 DAY").isEqualTo("DAY");
                assertThat(props.getContentLimit().getQueryString())
                        .as("queryString 限长默认 -1（不限）").isEqualTo(-1);
                assertThat(props.getContentLimit().getStrategy().name())
                        .as("超限策略默认 TRUNC").isEqualTo("TRUNC");
            });
        }

        @Test
        @DisplayName("嵌套属性绑定：开关、持久化模式、限长、事件名映射、HTTP 方法维度的 pattern")
        void fullBinding() {
            runner.withPropertyValues(
                            "jevokit.audit.log.enabled=true",
                            "jevokit.audit.log.enabled-mdc=true",
                            "jevokit.audit.log.persistent.mode=DB",
                            "jevokit.audit.log.persistent.db-migrate-period=MONTH",
                            "jevokit.audit.log.content-limit.query-string=100",
                            "jevokit.audit.log.content-limit.strategy=ABORT",
                            "jevokit.audit.log.event-types.name-mapper.QUERY=查询",
                            "jevokit.audit.log.event-types.patterns[GET][/api/user/**]=id,name")
                    .run(context -> {
                        AuditLogProperties props = context.getBean(AuditLogProperties.class);
                        assertThat(props.isEnabled()).as("enabled 应绑定").isTrue();
                        assertThat(props.isEnabledMdc()).as("enabled-mdc 应绑定").isTrue();
                        assertThat(props.getPersistent().getMode().name())
                                .as("mode=DB 应绑定").isEqualTo("DB");
                        assertThat(props.getPersistent().getDbMigratePeriod().name())
                                .as("db-migrate-period=MONTH 应绑定").isEqualTo("MONTH");
                        assertThat(props.getContentLimit().getQueryString())
                                .as("queryString=100 应绑定").isEqualTo(100);
                        assertThat(props.getContentLimit().getStrategy().name())
                                .as("strategy=ABORT 应绑定").isEqualTo("ABORT");
                        assertThat(props.getEventTypes().getNameMapper())
                                .as("name-mapper 应绑定").containsEntry("QUERY", "查询");
                        Map<String, List<String>> getPatterns =
                                props.getEventTypes().getPatterns().get(HttpMethod.GET);
                        assertThat(getPatterns)
                                .as("patterns[GET] 键应为 HttpMethod 枚举")
                                .containsEntry("/api/user/**", List.of("id", "name"));
                    });
        }
    }

    @Nested
    @DisplayName("XssProperties（jevokit.security.xss）")
    class Xss {

        @Test
        @DisplayName("默认值：关闭、clear 模式、忽略响应")
        void defaults() {
            runner.run(context -> {
                XssProperties props = context.getBean(XssProperties.class);
                assertThat(props.isEnabled()).as("enabled 默认 false").isFalse();
                assertThat(props.getMode()).as("mode 默认 clear").isEqualTo(XssProperties.Mode.clear);
                assertThat(props.isIgnoreResponse()).as("ignoreResponse 默认 true").isTrue();
                assertThat(props.getUrlPatterns()).as("urlPatterns 默认空").isEmpty();
                assertThat(props.getAllTaintedPatterns())
                        .as("XSS 内建正则为空（由用户控制），默认无污染规则").isEmpty();
            });
        }

        @Test
        @DisplayName("绑定：开关/模式/url 清单/追加正则，getAllTaintedPatterns 返回用户追加的并集副本")
        void bindingAndMergedPatterns() {
            runner.withPropertyValues(
                            "jevokit.security.xss.enabled=true",
                            "jevokit.security.xss.mode=escape",
                            "jevokit.security.xss.ignore-response=false",
                            "jevokit.security.xss.url-patterns[0]=/api/*",
                            "jevokit.security.xss.url-patterns[1]=/web/*",
                            "jevokit.security.xss.addition-tainted-patterns[0]=script-tag")
                    .run(context -> {
                        XssProperties props = context.getBean(XssProperties.class);
                        assertThat(props.isEnabled()).as("enabled 应绑定").isTrue();
                        assertThat(props.getMode()).as("mode=escape 应绑定").isEqualTo(XssProperties.Mode.escape);
                        assertThat(props.isIgnoreResponse()).as("ignore-response=false 应绑定").isFalse();
                        assertThat(props.getUrlPatterns()).as("url-patterns 应按序绑定")
                                .containsExactly("/api/*", "/web/*");

                        List<String> merged = props.getAllTaintedPatterns();
                        assertThat(merged).as("追加正则应并入结果").contains("script-tag");

                        // 防御性副本：外部修改合并结果不应影响下次调用
                        merged.add("polluted");
                        assertThat(props.getAllTaintedPatterns())
                                .as("getAllTaintedPatterns 应返回副本").doesNotContain("polluted");
                    });
        }
    }

    @Nested
    @DisplayName("SqlInjectProperties（jevokit.security.sql-inject）")
    class SqlInject {

        @Test
        @DisplayName("宽松键名：camelCase 前缀回退到 kebab-case 规范前缀")
        void relaxedPrefixBinding() {
            runner.withPropertyValues(
                            "jevokit.security.sqlInject.enabled=true",
                            "jevokit.security.sqlInject.mode=reject")
                    .run(context -> {
                        SqlInjectProperties props = context.getBean(SqlInjectProperties.class);
                        assertThat(props.isEnabled())
                                .as("camelCase 前缀 sqlInject 应绑定到规范前缀 sql-inject").isTrue();
                        assertThat(props.getMode()).as("mode 应绑定").isEqualTo(SqlInjectProperties.Mode.reject);
                    });
        }

        @Test
        @DisplayName("getAllTaintedPatterns：内建正则（阳性对照）+ 用户追加的并集")
        void mergedPatternsContainBuiltins() {
            runner.withPropertyValues(
                            "jevokit.security.sql-inject.addition-tainted-patterns[0]=my-extra-rule")
                    .run(context -> {
                        SqlInjectProperties props = context.getBean(SqlInjectProperties.class);
                        List<String> merged = props.getAllTaintedPatterns();
                        // 阳性对照：先证明内建合并链路活着（测试自持字面量）
                        assertThat(merged).as("应包含已知内建 SQL 注入正则")
                                .contains("((?i)UPDATE[ ]+[a-zA-Z_0-9]*[ ]+SET)");
                        // 再证明用户追加生效
                        assertThat(merged).as("用户追加正则应并入").contains("my-extra-rule");
                    });
        }
    }

    @Nested
    @DisplayName("PathTraversalProperties（jevokit.security.path-traversal）")
    class PathTraversal {

        @Test
        @DisplayName("默认值：reject 模式（与 xss/sql 的 clear 不同，锁死差异）")
        void defaultModeIsReject() {
            runner.run(context -> {
                PathTraversalProperties props = context.getBean(PathTraversalProperties.class);
                assertThat(props.getMode()).as("path-traversal 默认 reject").isEqualTo(PathTraversalProperties.Mode.reject);
                assertThat(props.isEnabled()).as("enabled 默认 false").isFalse();
            });
        }

        @Test
        @DisplayName("绑定：宽松 camelCase 前缀 + 追加正则并入内建（阳性对照 ../）")
        void bindingAndMerge() {
            runner.withPropertyValues(
                            "jevokit.security.pathTraversal.enabled=true",
                            "jevokit.security.path-traversal.addition-tainted-patterns[0]=my-rule")
                    .run(context -> {
                        PathTraversalProperties props = context.getBean(PathTraversalProperties.class);
                        assertThat(props.isEnabled())
                                .as("camelCase 前缀 pathTraversal 应绑定到 path-traversal").isTrue();
                        List<String> merged = props.getAllTaintedPatterns();
                        // 阳性对照：先证明内建合并链路活着（测试自持字面量，非引用被测常量）
                        assertThat(merged).as("应包含已知内建路径穿越正则").contains("\\.\\./");
                        assertThat(merged).as("用户追加正则应并入").contains("my-rule");
                    });
        }
    }

    @Nested
    @DisplayName("CodeInjectProperties（jevokit.security.code-inject）")
    class CodeInject {

        @Test
        @DisplayName("默认值：关闭、非否定模式、namespaces 为 null")
        void defaults() {
            runner.run(context -> {
                CodeInjectProperties props = context.getBean(CodeInjectProperties.class);
                assertThat(props.isEnabled()).as("enabled 默认 false").isFalse();
                assertThat(props.isNegative()).as("negative 默认 false").isFalse();
                assertThat(props.getNamespaces()).as("namespaces 默认 null").isNull();
            });
        }

        @Test
        @DisplayName("绑定：namespaces Map（default 命名空间的正则清单）与 negative 开关")
        void binding() {
            runner.withPropertyValues(
                            "jevokit.security.code-inject.enabled=true",
                            "jevokit.security.code-inject.negative=true",
                            "jevokit.security.code-inject.namespaces.default[0]=java.lang.Runtime",
                            "jevokit.security.code-inject.namespaces.default[1]=javax.script.*")
                    .run(context -> {
                        CodeInjectProperties props = context.getBean(CodeInjectProperties.class);
                        assertThat(props.isEnabled()).as("enabled 应绑定").isTrue();
                        assertThat(props.isNegative()).as("negative 应绑定").isTrue();
                        assertThat(props.getNamespaces())
                                .as("default 命名空间正则应按序绑定")
                                .containsEntry("default", List.of("java.lang.Runtime", "javax.script.*"));
                    });
        }
    }
}
