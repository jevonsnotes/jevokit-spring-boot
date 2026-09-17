package io.github.jevonsnotes.jevokit.api.util;

/**
 * JSecure API 协议头名工具：按终端配置的请求头前缀拼接实际头名（如 {@code X-JS-ClientId}）。
 * 使用方式：{@code rebuildHeader(name, prefix)}——prefix 已以 {@code -} 结尾则直接拼接，否则补 {@code -}，
 * 保证两种前缀写法（{@code X-JS-} / {@code X-JS}）产出一致头名；客户端组装与服务端解析共用本规则。
 */
public class ApiUtil {
    public static String rebuildHeader(String key, String prefix) {
        return prefix.endsWith("-") ? prefix + key : prefix + "-" + key;
    }
}
