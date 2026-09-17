package io.github.jevonsnotes.jevokit.security.xss.bean;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HtmlSafeList（jsoup 白名单扩展）真假矩阵：
 * 真——攻击样本（script 标签、事件属性、javascript: URI）必须被判不安全/被清除；
 * 假——正常富文本（白名单标签、相对路径图片、base64 图片）零误伤。
 */
class HtmlSafeListTest {

    private static final Document.OutputSettings OUTPUT_SETTINGS = new Document.OutputSettings()
            .escapeMode(Entities.EscapeMode.xhtml)
            .prettyPrint(false);

    @ParameterizedTest(name = "[真] script 载荷被判不安全: {0}")
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<SCRIPT SRC=http://evil.example.com/xss.js></SCRIPT>",
            "<img src=x onerror=alert(1)>",
            "<a href=\"javascript:alert(1)\">click</a>",
            "<a href='JaVaScRiPt:alert(1)'>click</a>",
            "<iframe src=\"javascript:alert(1)\"></iframe>",
            "<body onload=alert(1)>text</body>"
    })
    void attackPayloadsAreInvalid(String payload) {
        assertThat(Jsoup.isValid(payload, HtmlSafeList.INSTANCE))
                .as("攻击样本必须被白名单判为不安全: %s", payload)
                .isFalse();
    }

    @ParameterizedTest(name = "[真] script 载荷被清除: {0}")
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "<a href=\"javascript:alert(1)\">click</a>"
    })
    void attackPayloadsAreCleaned(String payload) {
        String cleaned = Jsoup.clean(payload, "", HtmlSafeList.INSTANCE, OUTPUT_SETTINGS);
        assertThat(cleaned.toLowerCase())
                .as("清洗结果不得残留 script/onerror/javascript: 载荷，实际: %s", cleaned)
                .doesNotContain("<script")
                .doesNotContain("onerror")
                .doesNotContain("javascript:");
    }

    @ParameterizedTest(name = "[假] 正常富文本零误伤: {0}")
    @ValueSource(strings = {
            "<b>加粗文本</b>",
            "<p>段落与 <a href=\"https://example.com?a=1&b=2\" title=\"链接\">链接</a></p>",
            "<img src=\"images/logo.png\" alt=\"相对路径图片\">",
            "<img src=\"data:;base64,iVBORw0KGgo=\">",
            "<table summary=\"表\"><tr><td colspan=\"2\">单元格</td></tr></table>",
            "纯中文文本，无任何标签"
    })
    void benignRichTextStaysValid(String content) {
        assertThat(Jsoup.isValid(content, HtmlSafeList.INSTANCE))
                .as("正常富文本必须被白名单放行: %s", content)
                .isTrue();
    }

    @Test
    @DisplayName("[假] 清洗后白名单标签/属性/中文内容保留")
    void cleanKeepsWhitelistedTagsAndContent() {
        String cleaned = Jsoup.clean("<p>你好<b>世界</b></p>", "", HtmlSafeList.INSTANCE, OUTPUT_SETTINGS);
        assertThat(cleaned)
                .as("白名单标签结构与中文内容必须原样保留")
                .isEqualTo("<p>你好<b>世界</b></p>");
    }

    @Test
    @DisplayName("[真] javascript: 前缀的 href/src 属性被丢弃，元素本体保留")
    void javascriptProtocolAttributeIsDropped() {
        String cleaned = Jsoup.clean("<a href=\"javascript:alert(1)\">点我</a>", "", HtmlSafeList.INSTANCE, OUTPUT_SETTINGS);
        assertThat(cleaned)
                .as("javascript: href 必须被剥离，链接文本保留: %s", cleaned)
                .contains("点我")
                .doesNotContain("javascript");
    }

    @Test
    @DisplayName("[假] 相对路径与 https 协议的图片地址保留")
    void relativeAndHttpsImgSrcAreKept() {
        String cleaned = Jsoup.clean("<img src=\"imgs/a.png\"><img src=\"https://cdn.example.com/b.png\">",
                "", HtmlSafeList.INSTANCE, OUTPUT_SETTINGS);
        assertThat(cleaned)
                .as("相对路径与 https 图片 src 不在协议黑名单拦截范围内，应保留")
                .contains("imgs/a.png")
                .contains("https://cdn.example.com/b.png");
    }

    @Test
    @DisplayName("INSTANCE 为单例且是 Safelist 子类（防御性回归）")
    void instanceIsSafelistSingleton() {
        assertThat(HtmlSafeList.INSTANCE)
                .as("INSTANCE 必须是 Safelist 实例且自持有")
                .isInstanceOf(Safelist.class)
                .isSameAs(HtmlSafeList.INSTANCE);
    }
}
