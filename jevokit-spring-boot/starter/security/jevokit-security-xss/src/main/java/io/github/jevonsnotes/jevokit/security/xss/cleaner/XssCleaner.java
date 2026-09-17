package io.github.jevonsnotes.jevokit.security.xss.cleaner;

/**
 * XSS 清洗器 SPI：对单个字符串值按当前请求模式（escape/reject/clear）执行防护。
 * 使用方式：由 XssConfiguration 注册默认实现（@ConditionalOnMissingBean 可覆盖），
 * 表单/JSON/XML 入站链路与响应出站链路统一经本接口清洗。
 */
public interface XssCleaner {
    /**
     * 清洗单个字符串值（escape/reject/clear 三模式语义由实现定义，默认实现见 DefaultXssCleaner）。
     *
     * @param content 待清洗内容；空白（null/空/全空白）时原样返回
     * @return escape 返回实体化转义串；clear 返回白名单净化后的 HTML 片段；reject 未命中时返回原串
     * @throws io.github.jevonsnotes.jevokit.security.xss.exception.XssException reject 模式命中污染时抛出（拒绝交互）
     */
    String clean(String content);
}
