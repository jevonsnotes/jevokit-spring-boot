package springbootkit.security.code.inject.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import springbootkit.configuration.security.code.inject.CodeInjectProperties;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CodeInjectUtil 真假矩阵：按命名空间正则白名单/黑名单判定安全性。
 * negative=false（默认）：全部规则命中才安全；negative=true：任一规则命中即不安全。
 */
class CodeInjectUtilTest {

    /** 初始化静态 properties（与生产 @Autowired initProperties 同通路） */
    private void initProperties(Map<String, List<String>> namespaces, boolean negative) {
        CodeInjectProperties properties = new CodeInjectProperties();
        properties.setNamespaces(namespaces);
        properties.setNegative(negative);
        new CodeInjectUtil().initProperties(properties);
    }

    @Test
    @DisplayName("[真] 正向白名单：非法脚本片段不匹配规则 → 不安全")
    void unsafeWhenNotMatchingWhitelist() {
        initProperties(Map.of("default", List.of("[a-z0-9_]+")), false);
        assertThat(CodeInjectUtil.isSafe("alert(1); drop table x"))
                .as("含括号分号空格的脚本片段不匹配白名单规则，必须判不安全")
                .isFalse();
    }

    @Test
    @DisplayName("[假] 正向白名单：纯标识符零误伤")
    void safeWhenMatchingAllWhitelistRules() {
        initProperties(Map.of("default", List.of("[a-z0-9_]+")), false);
        assertThat(CodeInjectUtil.isSafe("order_no_2024"))
                .as("纯小写字母数字下划线必须判安全")
                .isTrue();
    }

    @Test
    @DisplayName("正向白名单：多条规则须全部命中（任一不中即不安全）")
    void safeOnlyWhenAllRulesMatch() {
        initProperties(Map.of("default", List.of("[a-z0-9_]+", "^[a-z].*")), false);
        assertThat(CodeInjectUtil.isSafe("order_1")).as("同时满足两条规则应安全").isTrue();
        assertThat(CodeInjectUtil.isSafe("1_order")).as("第二条规则（小写开头）不满足必须不安全").isFalse();
    }

    @Test
    @DisplayName("[真] 反向黑名单（negative=true）：命中危险关键字即不安全")
    void unsafeWhenMatchingBlacklist() {
        initProperties(Map.of("expr", List.of(".*Runtime.*", ".*exec.*")), true);
        assertThat(CodeInjectUtil.isSafe("expr", "Runtime.getRuntime().exec(cmd)"))
                .as("命中 Runtime/exec 黑名单必须判不安全")
                .isFalse();
    }

    @Test
    @DisplayName("[假] 反向黑名单：普通文本不命中黑名单零误伤")
    void safeWhenNotMatchingBlacklist() {
        initProperties(Map.of("expr", List.of(".*Runtime.*", ".*exec.*")), true);
        assertThat(CodeInjectUtil.isSafe("expr", "正常的表达式文本"))
                .as("不命中黑名单必须判安全")
                .isTrue();
    }

    @Test
    @DisplayName("指定命名空间与默认命名空间互相独立")
    void namespaceIsolation() {
        initProperties(Map.of(
                "default", List.of("[a-z]+"),
                "numeric", List.of("[0-9]+")), false);
        assertThat(CodeInjectUtil.isSafe("numeric", "12345")).as("numeric 命名空间命中数字规则").isTrue();
        assertThat(CodeInjectUtil.isSafe("numeric", "abcde")).as("numeric 命名空间不匹配字母").isFalse();
        assertThat(CodeInjectUtil.isSafe("abcde")).as("缺省调用走 default 命名空间").isTrue();
    }

    @Test
    @DisplayName("未知命名空间与无规则命名空间默认安全（fail-open 语义固化）")
    void unknownNamespaceDefaultsSafe() {
        initProperties(Map.of("default", List.of("[a-z]+")), false);
        assertThat(CodeInjectUtil.isSafe("not-configured", "anything();"))
                .as("未配置规则返回 true（javadoc 契约：不存在规则时默认安全）")
                .isTrue();
    }

    @Test
    @DisplayName("空值与空规则集合默认安全")
    void blankValueAndEmptyRulesDefaultSafe() {
        initProperties(Map.of("default", List.of("[a-z]+")), false);
        assertThat(CodeInjectUtil.isSafe((String) null)).as("null 值默认安全").isTrue();
        assertThat(CodeInjectUtil.isSafe("")).as("空串默认安全").isTrue();

        initProperties(Map.of("empty", List.of()), false);
        assertThat(CodeInjectUtil.isSafe("empty", "script")).as("空规则列表默认安全").isTrue();
    }

    @Test
    @DisplayName("[真] errorIfUnsafe：不安全值抛 RuntimeException 并携带命名空间与值")
    void errorIfUnsafeThrowsWithDetails() {
        initProperties(Map.of("default", List.of("[a-z0-9_]+")), false);
        assertThatThrownBy(() -> CodeInjectUtil.errorIfUnsafe("alert(1)"))
                .as("不安全值必须抛异常")
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("default")
                .hasMessageContaining("alert(1)");
    }

    @Test
    @DisplayName("[假] errorIfUnsafe：安全值原样返回")
    void errorIfUnsafeReturnsValue() {
        initProperties(Map.of("default", List.of("[a-z0-9_]+")), false);
        assertThat(CodeInjectUtil.errorIfUnsafe("order_no_1"))
                .as("安全值必须原样返回")
                .isEqualTo("order_no_1");
    }

    @Test
    @DisplayName("errorIfUnsafe 支持指定命名空间")
    void errorIfUnsafeWithNamespace() {
        initProperties(Map.of("expr", List.of("[0-9]+")), false);
        assertThat(CodeInjectUtil.errorIfUnsafe("expr", "123")).isEqualTo("123");
        assertThatThrownBy(() -> CodeInjectUtil.errorIfUnsafe("expr", "abc"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("expr");
    }
}
