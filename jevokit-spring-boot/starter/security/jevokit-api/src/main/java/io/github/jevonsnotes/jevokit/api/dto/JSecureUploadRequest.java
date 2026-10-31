package io.github.jevonsnotes.jevokit.api.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 上传通道（{@code JSecureClient.upload(...)}.prepare）的组装产物：一次上传请求的全部外发要素，
 * 也是自定义传输载体（{@code execute(IUploadRequest)}）回调的入参类型。
 * 传输载体由调用方自选（OkHttp/RestTemplate/MockMvc 等）：meta part 与协议头发根请求，
 * push 文件按 {@link FilePart} 逐个以 part 名 "files"、filename=fileId 附加。
 *
 * <p>关键约束：metaJson 为协议信封密文形态（含 SM4 密文与 SM2 签名），须原样作为
 * name=meta、Content-Type=application/json 的 part 发送，不可再转义/拼接。
 */
@Getter
@RequiredArgsConstructor
public class JSecureUploadRequest {

    /** meta part 内容：JSecureRequest 协议信封 JSON（业务数据 + 文件摘要清单，已加密签名）。 */
    private final String metaJson;

    /** 根请求协议头（client-id/timestamp/nonce/signature，须逐个透传）。 */
    private final Map<String, String> headers;

    /** push 文件 part 清单（pull/refer 无文件 part，为空列表）。 */
    private final List<FilePart> files;

    /** push 文件 part：part 名固定 "files"，filename = fileId（服务端按此对齐摘要清单）。 */
    @Getter
    @RequiredArgsConstructor
    public static class FilePart {

        /** 对齐键：与摘要清单条目的 fileId 一致，作为 part 的 filename。 */
        private final String fileId;

        /** 文件内容。 */
        private final byte[] content;

        /** part 内容类型（缺省 application/octet-stream）。 */
        private final String contentType;

        /** multipart part 名（固定 "files"）。 */
        public String partName() {
            return "files";
        }
    }
}
