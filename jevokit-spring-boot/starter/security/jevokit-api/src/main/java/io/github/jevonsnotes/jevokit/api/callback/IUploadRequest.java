package io.github.jevonsnotes.jevokit.api.callback;

import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadRequest;

/**
 * JSecure 上传协议的传输载体回调：{@code JSecureClient.upload(...).execute} 借本接口把
 * "实际发起 multipart 请求"外交给调用方，门面只负责协议组装与结果解析，不绑死任何 HTTP 客户端。
 * 使用方式：lambda 实现 {@link #call(String, JSecureUploadRequest)}，内部用任意客户端
 * （OkHttp/RestTemplate 等）按 payload 三要素（meta part + 协议头 + 文件 part）发送并返回响应字符串。
 * <p>关键约束：{@link JSecureUploadRequest#getMetaJson()} 须作为 name=meta、
 * Content-Type=application/json 的 part 原样发送（不可再转义/拼接）；协议头挂在根请求上原样透传；
 * 文件 part 逐个以 part 名 "files"、filename=fileId 附加；返回值应为响应体原文（密文 JSON），
 * 由门面完成解密验签。
 */
public interface IUploadRequest {

    /**
     * 发送一次安全上传请求。
     *
     * @param url     请求地址
     * @param payload 外发要素全集（meta part + 协议头 + 文件 part）
     * @return 响应体原文（JSecure 响应信封密文 JSON）
     */
    String call(String url, JSecureUploadRequest payload);
}
