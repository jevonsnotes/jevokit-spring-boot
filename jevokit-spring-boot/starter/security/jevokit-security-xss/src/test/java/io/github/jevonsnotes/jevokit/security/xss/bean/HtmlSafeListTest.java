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

    // ------------------------- 实体/控制符混淆绕过修复回归 -------------------------

    @ParameterizedTest(name = "[真] 实体/控制符混淆的 javascript 载荷被判不安全: {0}")
    @ValueSource(strings = {
            // 实体解码后残留控制符（浏览器剥 \t\n\r 后按 javascript: 执行）——修复前全部漏判
            "<a href=\"java&#9;script:alert(1)\">x</a>",
            "<a href=\"jav&#x0A;ascript:alert(1)\">x</a>",
            "<a href=\"jav&#x0d;ascript:alert(1)\">x</a>",
            "<a href=\"JAVA&#X9;SCRIPT:alert(1)\">x</a>",
            // 实体解码后即为 javascript（jsoup 解码与浏览器一致，既有检查可命中，固化防回退）
            "<a href=\"&#106;avascript:alert(1)\">x</a>",
            "<a href=\"&#106;av&#97;script:alert(1)\">x</a>",
            "<a href=\"jav&#x41;script:alert(1)\">x</a>",
            // 首部空格（浏览器 URL 解析剥首尾空格/C0 后可执行）
            "<a href=\" javascript:alert(1)\">x</a>",
            "<a href=\"\tjavascript:alert(1)\">x</a>",
            // src 属性同面
            "<img src=\"java&#115;cript:alert(1)\">"
    })
    void entityObfuscatedJavascriptPayloadsAreInvalid(String payload) {
        assertThat(Jsoup.isValid(payload, HtmlSafeList.INSTANCE))
                .as("实体/控制符混淆的 javascript 载荷必须被判不安全: %s", payload)
                .isFalse();
    }

    @Test
    @DisplayName("[真] 实体混淆 javascript href 清洗后属性被剥离，链接文本保留")
    void entityObfuscatedJavascriptHrefIsStrippedOnClean() {
        String cleaned = Jsoup.clean("<a href=\"java&#9;script:alert(1)\">点我</a>", "",
                HtmlSafeList.INSTANCE, OUTPUT_SETTINGS);
        assertThat(cleaned)
                .as("混淆形态 href 必须被剥离且不残留可执行形态: %s", cleaned)
                .contains("点我")
                .doesNotContain("href")
                .doesNotContain("script:");
    }

    @ParameterizedTest(name = "[假] 相对路径/锚点/query 特殊字符/字面量实体零误伤: {0}")
    @ValueSource(strings = {
            "<a href=\"/list?page=1&size=10\">x</a>",
            "<a href=\"./edit?id=9\">x</a>",
            "<a href=\"../up?token=a&b=c\">x</a>",
            "<a href=\"#frag\">x</a>",
            "<a href=\"https://example.com/p?a=1&b=2\">x</a>",
            // 双重转义字面量：DOM 值为 "java&#9;script:x"，浏览器不按 javascript scheme 执行，不得误拒
            "<a href=\"java&amp;#9;script:notes\">x</a>",
            // 锚点值内含实体解码出的制表符，属锚点导航非协议执行
            "<a href=\"#frag&#9;x\">x</a>",
            // 中部普通空格使 scheme 非法，浏览器不执行
            "<a href=\"java script:alert(1)\">x</a>"
    })
    void benignLinkFormsAreNotBrokenByNormalization(String payload) {
        assertThat(Jsoup.isValid(payload, HtmlSafeList.INSTANCE))
                .as("相对路径/锚点/query/字面量形态必须零误伤: %s", payload)
                .isTrue();
    }

    @Test
    @DisplayName("[真] 疑似实体的 &# 字面量 query（&#b 形态）被 jsoup 校验拒绝——既有行为固化，非本次规范化引入")
    void invalidNumericEntityLikeQueryStaysRejected() {
        // jsoup 层既有行为：纯 Safelist（无任何覆写）下该输入在 1.15.3 与 1.23.2 均判不安全
        //（&#b 为非法数字字符引用，jsoup 校验记为变更），与 HtmlSafeList 规范化判定无关。
        String payload = "<a href=\"/s?q=a&#b=c&d=1\">x</a>";
        assertThat(Jsoup.isValid(payload, HtmlSafeList.INSTANCE))
                .as("非法实体形态 query 的既有拒绝行为保持不变")
                .isFalse();
    }

    @Test
    @DisplayName("规范化判定矩阵：混淆形态命中、正常值与字面量不命中")
    void startsWithJavascriptSchemeNormalizationMatrix() {
        // 命中：控制符剥离 + 大小写 + 首尾空白（前缀语义与既有行为一致）
        assertThat(HtmlSafeList.startsWithJavascriptScheme("javascript:alert(1)")).isTrue();
        assertThat(HtmlSafeList.startsWithJavascriptScheme("java\tscript:alert(1)")).isTrue();
        assertThat(HtmlSafeList.startsWithJavascriptScheme("jav\nascript:alert(1)")).isTrue();
        assertThat(HtmlSafeList.startsWithJavascriptScheme("jav\rascript:alert(1)")).isTrue();
        assertThat(HtmlSafeList.startsWithJavascriptScheme("JAVASCRIPT:alert(1)")).isTrue();
        assertThat(HtmlSafeList.startsWithJavascriptScheme("JaVaScRiPt:alert(1)")).isTrue();
        assertThat(HtmlSafeList.startsWithJavascriptScheme(" javascript:alert(1)")).isTrue();
        assertThat(HtmlSafeList.startsWithJavascriptScheme("\tjavascript")).isTrue();
        // 不命中：正常链接形态
        assertThat(HtmlSafeList.startsWithJavascriptScheme("/list?page=1")).isFalse();
        assertThat(HtmlSafeList.startsWithJavascriptScheme("./edit?id=9")).isFalse();
        assertThat(HtmlSafeList.startsWithJavascriptScheme("../up")).isFalse();
        assertThat(HtmlSafeList.startsWithJavascriptScheme("#frag")).isFalse();
        assertThat(HtmlSafeList.startsWithJavascriptScheme("https://example.com")).isFalse();
        // 不命中：值中部普通空格（非法 scheme，浏览器不执行）
        assertThat(HtmlSafeList.startsWithJavascriptScheme("java script:alert(1)")).isFalse();
        // 不命中：字面量实体不做二次解码
        assertThat(HtmlSafeList.startsWithJavascriptScheme("java&#9;script:x")).isFalse();
        assertThat(HtmlSafeList.startsWithJavascriptScheme("")).isFalse();
    }
}
