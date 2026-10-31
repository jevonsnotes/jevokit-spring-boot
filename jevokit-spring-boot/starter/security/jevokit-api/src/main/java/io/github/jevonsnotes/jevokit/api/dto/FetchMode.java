package io.github.jevonsnotes.jevokit.api.dto;

/**
 * 上传文件获取方式：决定服务端对每个文件条目的校验边界与获取职责。
 * <ul>
 *   <li>{@link #push}：随本次请求 multipart 直传（文件 part），服务端无感校验做元数据 + 内容双层校验；</li>
 *   <li>{@link #pull}：文件在第三方 URL，服务端元数据校验 + 域名白名单（SSRF 硬防线），
 *       实际下载由业务显式调 {@code JSecureFileFetcher.fetch}（时机是业务决策）；</li>
 *   <li>{@link #refer}：引用本系统已存在的文件（fileId 由业务钩子
 *       {@code JSecureUploadFileRefChecker} 判定存在性）。</li>
 * </ul>
 * <p>枚举名即协议线上取值（小写），fastjson2/Jackson 按名反序列化，无需额外适配。
 */
public enum FetchMode {
    /** 随请求 multipart 直传，服务端做元数据 + 内容（size/digest）双层校验。 */
    push,
    /** 文件在第三方 URL：元数据校验 + fetch-url-whitelist 域名白名单；下载由业务显式发起。 */
    pull,
    /** 引用系统内已存在文件：由 JSecureUploadFileRefChecker 钩子校验存在性。 */
    refer
}
