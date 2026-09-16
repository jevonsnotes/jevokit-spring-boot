package springbootkit.security.sql.inject.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import springbootkit.configuration.holder.ConfigHolder;
import springbootkit.configuration.security.sql.inject.SqlInjectProperties;
import springbootkit.security.sql.inject.exception.SqlInjectException;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SqlInjectUtil 真假矩阵（默认污染表：UPDATE..SET / INSERT INTO / DELETE FROM / 空格+(AND|OR|WHERE)+等式）。
 * union select、注释截断等攻击形态默认表不覆盖，经 additionTaintedPatterns 用户扩展后覆盖（见用例）。
 */
class SqlInjectUtilTest {

    private final SqlInjectProperties properties = new SqlInjectProperties();

    @AfterEach
    void tearDown() {
        ConfigHolder.removeSI();
    }

    private void mode(SqlInjectProperties.Mode mode) {
        ConfigHolder.getSIMeta().setMode(mode);
    }

    // ------------------------- clear 模式 -------------------------

    @ParameterizedTest(name = "[真] clear 清除默认表攻击样本: {0}")
    @ValueSource(strings = {
            "name' OR 1=1 --",
            "id=1 OR admin=true",
            "a AND b=c",
            "x WHERE id=1",
            "UPDATE users SET password='hacked'",
            "insert into admin values(1)",
            "DELETE FROM orders WHERE id=1"
    })
    void clearModeCleansDefaultAttacks(String payload) {
        mode(SqlInjectProperties.Mode.clear);
        String cleaned = SqlInjectUtil.clean(payload, properties.getAllTaintedPatterns());
        assertThat(cleaned)
                .as("clear 模式必须移除默认污染表命中的片段: [%s] -> [%s]", payload, cleaned)
                .isNotEqualTo(payload);
        assertThat(cleaned.toUpperCase())
                .as("清洗后不得残留 UPDATE..SET / INSERT INTO / DELETE FROM / (AND|OR|WHERE) 等式片段")
                .doesNotContain("UPDATE ")
                .doesNotContain("INSERT INTO")
                .doesNotContain("DELETE FROM");
    }

    @ParameterizedTest(name = "[假] clear 正常报文零误伤: {0}")
    @ValueSource(strings = {
            "普通中文查询条件：张三的订单",
            "coffee or tea",
            "张三 和 李四",
            "a.b(c)[d]{e} 正则元字符文本",
            "{\"action\":\"delete\",\"from\":\"sender\"}",
            "https://example.com/search?a=1&b=2",
            "2024-11-20 10:42:54"
    })
    void clearModeKeepsBenignContent(String content) {
        mode(SqlInjectProperties.Mode.clear);
        assertThat(SqlInjectUtil.clean(content, properties.getAllTaintedPatterns()))
                .as("正常报文必须原样返回: %s", content)
                .isEqualTo(content);
    }

    @Test
    @DisplayName("[真] clear + additionTaintedPatterns：union select / 注释截断被清除")
    void clearModeAppliesAdditionTaintedPatterns() {
        properties.setAdditionTaintedPatterns(List.of(
                "((?i)union[ ]+select)",
                "--",
                "/\\*.*?\\*/"));
        mode(SqlInjectProperties.Mode.clear);
        String cleaned = SqlInjectUtil.clean(
                "1 UNION SELECT password FROM users -- and /* block */ tail",
                properties.getAllTaintedPatterns());
        assertThat(cleaned)
                .as("union select、行注释、块注释均应被追加规则清除，实际: %s", cleaned)
                .doesNotContainIgnoringCase("union select")
                .doesNotContain("--")
                .doesNotContain("/*")
                .contains("password FROM users");
    }

    // ------------------------- reject 模式 -------------------------

    @ParameterizedTest(name = "[真] reject 拒绝攻击样本: {0}")
    @ValueSource(strings = {
            "name' OR 1=1 --",
            "UPDATE users SET role='admin'",
            "INSERT INTO logs VALUES(1)",
            "DELETE FROM users WHERE 1=1"
    })
    void rejectModeThrowsOnAttacks(String payload) {
        mode(SqlInjectProperties.Mode.reject);
        assertThatThrownBy(() -> SqlInjectUtil.clean(payload, properties.getAllTaintedPatterns()))
                .as("reject 模式必须对攻击样本抛 SqlInjectException: %s", payload)
                .isInstanceOf(SqlInjectException.class);
    }

    @ParameterizedTest(name = "[假] reject 放行正常报文: {0}")
    @ValueSource(strings = {
            "普通中文查询条件",
            "coffee or tea",
            "订单号 ORD-2024-0001"
    })
    void rejectModePassesBenignContent(String content) {
        mode(SqlInjectProperties.Mode.reject);
        assertThatCode(() -> assertThat(SqlInjectUtil.clean(content, properties.getAllTaintedPatterns()))
                .as("正常报文必须原样放行: %s", content)
                .isEqualTo(content))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[真] 追加规则（文档示例四条正则背书）：union select / -- / # / 块注释字符类形态")
    void additionTaintedPatternsDocExamplePatterns() {
        properties.setAdditionTaintedPatterns(List.of(
                "((?i)union[ ]+select)", "--", "#", "[/][*].*?[*][/]"));
        mode(SqlInjectProperties.Mode.clear);
        String cleaned = SqlInjectUtil.clean(
                "1 UNION SELECT password -- tail # note /* block */ end",
                properties.getAllTaintedPatterns());
        assertThat(cleaned)
                .as("文档示例四条正则全部生效，实际: %s", cleaned)
                .doesNotContainIgnoringCase("union select")
                .doesNotContain("--")
                .doesNotContain("#")
                .doesNotContain("/*")
                .contains("password");
    }

    @Test
    @DisplayName("[真] 追加规则：# 单独触发拒绝（reject 模式，无默认表命中干扰）")
    void additionHashCommentReject() {
        properties.setAdditionTaintedPatterns(List.of("#"));
        mode(SqlInjectProperties.Mode.reject);
        assertThatThrownBy(() -> SqlInjectUtil.clean("price # discount note", properties.getAllTaintedPatterns()))
                .as("仅命中 # 追加规则也必须拒绝")
                .isInstanceOf(SqlInjectException.class);
    }

    // ------------------------- 边界 -------------------------

    @Test
    @DisplayName("空串/null/空白串原样返回")
    void blankContentIsReturnedAsIs() {
        for (SqlInjectProperties.Mode m : SqlInjectProperties.Mode.values()) {
            mode(m);
            assertThat(SqlInjectUtil.clean(null, properties.getAllTaintedPatterns()))
                    .as("null 在 %s 模式原样返回", m).isNull();
            assertThat(SqlInjectUtil.clean("", properties.getAllTaintedPatterns()))
                    .as("空串在 %s 模式原样返回", m).isEmpty();
            assertThat(SqlInjectUtil.clean("   ", properties.getAllTaintedPatterns()))
                    .as("空白串在 %s 模式原样返回", m).isEqualTo("   ");
        }
    }

    @Test
    @DisplayName("污染模式列表为空时原样返回（防御性）")
    void emptyPatternsPassthrough() {
        mode(SqlInjectProperties.Mode.clear);
        assertThat(SqlInjectUtil.clean("UPDATE users SET a=1", Collections.emptyList()))
                .as("空规则列表时不得清洗")
                .isEqualTo("UPDATE users SET a=1");
    }

    @Test
    @DisplayName("超长中文报文（10 万字符）clear 模式原样返回")
    void longBenignContentPassthrough() {
        mode(SqlInjectProperties.Mode.clear);
        String longText = "安全中文长报文".repeat(20_000);
        assertThat(SqlInjectUtil.clean(longText, properties.getAllTaintedPatterns()))
                .as("无命中的长报文必须原样返回")
                .isEqualTo(longText);
    }
}
