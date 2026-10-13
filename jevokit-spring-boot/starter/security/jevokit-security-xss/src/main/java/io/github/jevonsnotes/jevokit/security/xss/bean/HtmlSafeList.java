package io.github.jevonsnotes.jevokit.security.xss.bean;


import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * XSS 净化白名单：基于 jsoup Safelist 扩展的标签/属性/协议放行清单，
 * clear 模式下不在清单内的标签与属性一律剥离。
 * 关键约束（为什么 a.href 未限协议）：限定 http/https 协议会把相对路径链接一并过滤掉，
 * 业务常用站内相对链接，故仅对引用类属性（cite）限协议；如需更严可自行补充 addProtocols。
 */
public class HtmlSafeList extends Safelist {
    /** 全局共享白名单实例（无状态，线程安全）。 */
    public static final Safelist INSTANCE = new HtmlSafeList();

    /**
     * scheme 混淆剥离模式：URL 规范化时浏览器会从值中剥除的字符（\t、\r、\n）。
     * 值中部的普通空格浏览器不剥除（"java script:" 属非法 scheme 不可执行），故不剥，避免误伤语义。
     */
    private static final Pattern SCHEME_OBFUSCATION_STRIP = Pattern.compile("[\\t\\r\\n]");

    /**
     * 构造放行清单：常见排版标签 + 有限属性 + 引用协议白名单（内容见类注释与下方清单）。
     */
    public HtmlSafeList(){
        addTags("a", "b", "blockquote", "br", "caption", "cite", "code", "col", "colgroup", "dd", "div", "span", "embed", "object", "dl", "dt",
                "em", "h1", "h2", "h3", "h4", "h5", "h6", "i", "img", "li", "ol", "p", "pre", "q", "small",
                "strike", "strong", "sub", "sup", "table", "tbody", "td", "tfoot", "th", "thead", "tr", "u", "ul");

        addAttributes("a", "href", "title", "target");
        addAttributes("blockquote", "cite");
        addAttributes("col", "span");
        addAttributes("colgroup", "span");
        addAttributes("img", "align", "alt", "src", "title");
        addAttributes("ol", "start");
        addAttributes("q", "cite");
        addAttributes("table", "summary");
        addAttributes("td", "abbr", "axis", "colspan", "rowspan", "width");
        addAttributes("th", "abbr", "axis", "colspan", "rowspan", "scope", "width");
        addAttributes("video", "src", "autoplay", "controls", "loop", "muted", "poster", "preload");
        addAttributes("object", "width", "height", "classid", "codebase");
        addAttributes("param", "name", "value");
        addAttributes("embed", "src", "quality", "width", "height", "allowFullScreen", "allowScriptAccess", "flashvars", "name", "type", "pluginspage");

        addAttributes(":all", "class", "style", "height", "width", "type", "id", "name");

        addProtocols("blockquote", "cite", "http", "https");
        addProtocols("cite", "cite", "http", "https");
        addProtocols("q", "cite", "http", "https");

        //如果添加以下的协议，那么href 必须是http、 https 等开头，相对路径则被过滤掉了
        //addProtocols("a", "href", "ftp", "http", "https", "mailto", "tel");

        //如果添加以下的协议，那么src必须是http 或者 https 开头，相对路径则被过滤掉了，
        //所以必须注释掉，允许相对路径的图片资源
        //addProtocols("img", "src", "http", "https");
    }

    @Override
    /**
     * 属性放行判定（jsoup 净化回调）：按构造器登记的属性白名单决定是否保留该属性。
     * jsoup 1.23.x 起 Safelist#isSafeAttribute 由 protected 提升为 public，覆写须同步 public。
     * src/href 的 javascript 协议检查在规范化值上进行，防实体/控制符混淆绕过（见
     * {@link #startsWithJavascriptScheme(String)}）。
     *
     * @param tagName 标签名
     * @param el      元素
     * @param attr    待判定属性
     * @return true 保留属性；false 剥离
     */
    public boolean isSafeAttribute(String tagName, Element el, Attribute attr) {
        //不允许 javascript 开头的 src 和 href（规范化后判定）
        if ("src".equalsIgnoreCase(attr.getKey()) || "href".equalsIgnoreCase(attr.getKey())) {
            String value = attr.getValue();
            if (StringUtils.hasText(value) && startsWithJavascriptScheme(value)) {
                return false;
            }
        }
        //允许 base64 的图片内容
        if ("img".equals(tagName) && "src".equals(attr.getKey()) && attr.getValue().startsWith("data:;base64")) {
            return true;
        }
        return super.isSafeAttribute(tagName, el, attr);
    }

    /**
     * 规范化后判断 src/href 值是否 javascript 协议开头（前缀语义与既有判定保持一致，不收窄不扩面）。
     * <p>规范化 = 小写（Locale.ROOT，防默认 locale 变体漏判）+ 剥除值内 \t\r\n + trim 首尾 ≤0x20
     * 字符（空格与 C0 控制符），再匹配 javascript 前缀——与浏览器 URL 解析器的规范化行为对齐。
     * <p>设计依据（jsoup 1.23.2 实证）：jsoup 解析属性时已按 HTML 规范解码实体（&#9;→制表符、
     * &#106;→j，与浏览器 DOM 一致），故本方法不做二次实体解码——二次解码会把
     * "java&amp;#9;script:x" 解码后残留的字面量 "java&#9;script:x" 误判为 javascript 协议，
     * 而该字面量形态浏览器并不会按 javascript scheme 执行。
     * <p>不误伤边界：/path、./path、../path、#anchor、含 & 与 &# 字面量的 query、http/https URL
     * 均不以 javascript 开头，判定不受影响。
     *
     * @param value 属性原始值（jsoup 已完成实体解码）
     * @return true 规范化后以 javascript 开头，应剥离该属性
     */
    static boolean startsWithJavascriptScheme(String value) {
        String normalized = SCHEME_OBFUSCATION_STRIP.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("").trim();
        return normalized.startsWith("javascript");
    }
}
