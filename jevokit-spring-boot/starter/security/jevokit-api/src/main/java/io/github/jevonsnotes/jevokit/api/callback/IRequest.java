package io.github.jevonsnotes.jevokit.api.callback;

import java.util.Map;

/**
 * JSecure 协议的传输载体回调：{@code JSRequestExecutor.execute} 借本接口把"实际发起 HTTP 请求"
 * 外交给调用方，执行器只负责协议组装与结果解析，不绑死任何 HTTP 客户端。
 * 使用方式：lambda 实现 {@link #call(String, String, Map)}，内部用任意客户端（RestTemplate/OkHttp 等）
 * 携带协议请求头发送报文体并返回响应字符串。
 * <p>关键约束：headers 已含终端签名头（clientId/timestamp/nonce/signature），须原样透传；
 * 返回值应为响应体原文（密文 JSON），由执行器完成解密验签。
 */
public interface IRequest {
    String call(String url, String body, Map<String, String> headers);
}
