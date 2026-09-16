package springbootkit.security.xss.cleaner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.util.HtmlUtils;
import springbootkit.configuration.holder.ConfigHolder;
import springbootkit.configuration.security.xss.XssProperties;
import springbootkit.security.xss.exception.XssException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DefaultXssCleaner 三种 mode（clear/escape/reject）真假矩阵。
 * mode 经 ConfigHolder ThreadLocal 下发，每个用例自设自清（用例独立可乱序）。
 */
class DefaultXssCleanerTest {

    private final XssProperties properties = new XssProperties();
    private final DefaultXssCleaner cleaner = new DefaultXssCleaner(properties);

    @AfterEach
    void tearDown() {
        ConfigHolder.removeXss();
    }

    private void mode(XssProperties.Mode mode) {
        ConfigHolder.getXssMeta().setMode(mode);
    }

    // ------------------------- clear 模式 -------------------------

    @ParameterizedTest(name = "[真] clear 清除载荷: {0}")
    @ValueSource(strings = {
            "<script>alert('xss')</script>",
            "<img src=x onerror=alert(1)>",
            "<a href=\"javascript:alert(1)\">点我</a>",
            "<svg/onload=alert(1)>",
            "<iframe src=\"http://evil.example.com\"></iframe>"
    })
    void clearModeNeutralizesAttacks(String payload) {
        mode(XssProperties.Mode.clear);
        String cleaned = cleaner.clean(payload);
        assertThat(cleaned.toLowerCase())
                .as("clear 模式输出不得残留活动载荷，实际: %s", cleaned)
                .doesNotContain("<script")
                .doesNotContain("onerror")
                .doesNotContain("onload")
                .doesNotContain("javascript:")
                .doesNotContain("<iframe");
    }

    @ParameterizedTest(name = "[假] clear 正常报文零误伤: {0}")
    @ValueSource(strings = {
            "普通中文报文，不含任何标记",
            "订单编号 ORD-2024-0001 与金额 123.45",
            "a.b(c)[d]{e} 正则元字符文本",
            "{\"key\": \"value\", \"nested\": {\"k\": \"v\"}}"
    })
    void clearModeKeepsBenignText(String content) {
        mode(XssProperties.Mode.clear);
        assertThat(cleaner.clean(content))
                .as("clear 模式下普通文本必须原样返回: %s", content)
                .isEqualTo(content);
    }

    @Test
    @DisplayName("[假] clear 模式：URL 参数中的 & 被 jsoup 实体转义（既有行为固化）")
    void clearModeEscapesAmpersandInUrlParam() {
        mode(XssProperties.Mode.clear);
        String cleaned = cleaner.clean("https://example.com/search?a=1&b=2");
        // 既有行为：jsoup xhtml escapeMode 会将正文中的 & 转义为 &amp;（见 BUG 条目）
        assertThat(HtmlUtils.htmlUnescape(cleaned))
                .as("转义后的 URL 参数经反转义必须还原为原文（语义零丢失）")
                .isEqualTo("https://example.com/search?a=1&b=2");
    }

    @Test
    @DisplayName("[真] clear 模式 + additionTaintedPatterns 追加清洗用户指定片段")
    void clearModeAppliesAdditionTaintedPatterns() {
        properties.setAdditionTaintedPatterns(List.of("(?i)onfocus", "EVILWORD"));
        mode(XssProperties.Mode.clear);
        String cleaned = cleaner.clean("文本带 onfocus 与 EVILWORD 标记");
        assertThat(cleaned)
                .as("追加污染模式命中的片段必须被移除")
                .isEqualTo("文本带  与  标记");
    }

    @Test
    @DisplayName("clear 模式：白名单富文本结构保留")
    void clearModeKeepsWhitelistedRichText() {
        mode(XssProperties.Mode.clear);
        assertThat(cleaner.clean("<p>你好<b>世界</b></p>"))
                .as("白名单标签应保留")
                .isEqualTo("<p>你好<b>世界</b></p>");
    }

    // ------------------------- escape 模式 -------------------------

    @Test
    @DisplayName("[真] escape 模式：载荷整体转义，无活动 HTML")
    void escapeModeEscapesPayload() {
        mode(XssProperties.Mode.escape);
        String escaped = cleaner.clean("<script>alert(1)</script>");
        assertThat(escaped)
                .as("escape 模式必须把标签转为 HTML 实体: %s", escaped)
                .isEqualTo("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    @DisplayName("[假] escape 模式：中文与字母数字保持明文，可无损还原")
    void escapeModeKeepsBenignTextReversible() {
        mode(XssProperties.Mode.escape);
        String source = "中文内容 with mixed English 123";
        assertThat(cleaner.clean(source))
                .as("escape 模式不得改动普通字符")
                .isEqualTo(source);
    }

    @Test
    @DisplayName("[假] escape 模式：JSON 转义报文反转义后无损")
    void escapeModeKeepsJsonEscapedPayloadReversible() {
        mode(XssProperties.Mode.escape);
        String source = "{\"key\": \"va\\\"lue\"}";
        assertThat(HtmlUtils.htmlUnescape(cleaner.clean(source)))
                .as("escape 只做 HTML 实体转义，反转义后必须还原原文")
                .isEqualTo(source);
    }

    @Test
    @DisplayName("escape 模式：引号被转义为实体（既有行为固化）")
    void escapeModeEscapesQuotes() {
        mode(XssProperties.Mode.escape);
        assertThat(cleaner.clean("a\"b"))
                .as("HtmlUtils.htmlEscape 会转义双引号")
                .isEqualTo("a&quot;b");
    }

    // ------------------------- reject 模式 -------------------------

    @ParameterizedTest(name = "[真] reject 拒绝载荷: {0}")
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "<a href=\"javascript:alert(1)\">x</a>",
            "<iframe></iframe>"
    })
    void rejectModeThrowsOnAttacks(String payload) {
        mode(XssProperties.Mode.reject);
        assertThatThrownBy(() -> cleaner.clean(payload))
                .as("reject 模式必须对攻击样本抛 XssException: %s", payload)
                .isInstanceOf(XssException.class);
    }

    @ParameterizedTest(name = "[假] reject 放行正常报文: {0}")
    @ValueSource(strings = {
            "普通中文报文",
            "<b>白名单加粗</b>",
            "<p>段落<a href=\"https://example.com\" title=\"t\">链接</a></p>"
    })
    void rejectModePassesBenignContent(String content) {
        mode(XssProperties.Mode.reject);
        assertThatCode(() -> assertThat(cleaner.clean(content)).isEqualTo(content))
                .as("reject 模式对白名单内容必须原样放行: %s", content)
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[真] reject 模式 + additionTaintedPatterns 追加拒绝")
    void rejectModeAppliesAdditionTaintedPatterns() {
        properties.setAdditionTaintedPatterns(List.of("(?i)union[ ]+select"));
        mode(XssProperties.Mode.reject);
        assertThatThrownBy(() -> cleaner.clean("1 UNION SELECT password FROM users"))
                .as("追加污染模式命中时 reject 必须抛异常")
                .isInstanceOf(XssException.class);
    }

    // ------------------------- 边界 -------------------------

    @Test
    @DisplayName("空串/null/空白串原样返回（三模式都不处理）")
    void blankContentIsReturnedAsIs() {
        for (XssProperties.Mode m : XssProperties.Mode.values()) {
            mode(m);
            assertThat(cleaner.clean(null)).as("null 在 %s 模式原样返回", m).isNull();
            assertThat(cleaner.clean("")).as("空串在 %s 模式原样返回", m).isEmpty();
            assertThat(cleaner.clean("   ")).as("空白串在 %s 模式原样返回", m).isEqualTo("   ");
        }
    }

    @Test
    @DisplayName("超长报文（10 万字符中文）clear 模式可正常清洗且长度不膨胀")
    void longContentIsHandled() {
        mode(XssProperties.Mode.clear);
        String longText = "中文安全内容".repeat(20_000);
        String cleaned = cleaner.clean(longText);
        assertThat(cleaned)
                .as("无载荷长报文必须原样返回")
                .isEqualTo(longText);
    }
}
