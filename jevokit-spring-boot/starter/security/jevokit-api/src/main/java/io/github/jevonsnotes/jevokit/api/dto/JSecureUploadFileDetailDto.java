package io.github.jevonsnotes.jevokit.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * JSecure 上传协议的单文件元数据条目（文件摘要清单的元素）。
 *
 * <p>三种获取方式由 {@link #fetchMode} 区分：push 条目必须有同名（part 名固定 "files"）
 * 且 filename = {@link #fileId} 的文件 part 与之对齐（对齐键，顺序无关）；pull 条目必须带
 * 合法 {@link #url}（域名须在终端 upload-fetch-url-whitelist 白名单内）；refer 条目由业务实现的
 * {@code JSecureUploadFileRefChecker} 钩子校验存在性。
 *
 * <p>安全约束：
 * <ul>
 *   <li>{@link #fileName} 仅为展示用途的原始文件名，<b>永不落盘</b>——服务端持久化命名一律使用
 *       服务端生成的新名（如 fileId + 服务端序号），防止路径穿越与原始名注入；</li>
 *   <li>{@link #size}/{@link #digest} 是完整性契约：push 模式下服务端按此做流式摘要比对，
 *       篡改/调包（两个文件互换 fileId）都会被拒；</li>
 *   <li>{@link #fetchToken} 为敏感字段，协议链路日志输出时自动脱敏（前 4 后 4 明文，其余掩码）。</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JSecureUploadFileDetailDto {

    /** 请求内唯一标识：push 模式即 multipart 文件 part 的 filename（对齐键，顺序无关）。 */
    private String fileId;

    /** 业务类型（invoice/id_card 等），终端级 format 白名单按它配置。 */
    private String fileType;

    /** 场景细化：如 front/back（身份证正反面）、页码。 */
    private String usage;

    /**
     * 原始文件名——<b>仅展示用途，永不落盘</b>。
     * 服务端持久化必须另行生成存储名，防止路径穿越/原始名注入。
     */
    private String fileName;

    /** 文件格式（pdf/jpg/png 等），按终端级 upload-format-whitelist 白名单校验。 */
    private String format;

    /** 文件字节数：digest 计算前先比（超上限/与实际不符即拒）；加密条目为密文字节数（即 wire 上 part 实际大小）。 */
    private Long size;

    /**
     * 摘要值（hex 小写），按 {@link #digestAlg} 算法流式比对。
     * 加密条目（{@link #encrypted}=true）的摘要按<b>加密前明文</b>计算——服务端解密后复核。
     */
    private String digest;

    /** 摘要算法：sha256（缺省）/ sm3 / md5（可选算法集与终端允许范围见 upload-digest-algorithms）。 */
    private String digestAlg = "sha256";

    /**
     * 该条目文件 part 是否为密文：客户端 {@code add(..., encrypt=true)} 时置位（SM4-CBC、
     * 随机 IV 前缀、密钥共用终端 SM4 密钥）。在签名信封内，不可伪造。
     * 服务端校验链解密后将明文填充 {@link #plainContent}，业务从条目直接拿明文。
     */
    private boolean encrypted;

    /**
     * 解密后的明文字节：仅服务端校验链填充（{@link #encrypted} 条目），wire 上不存在
     * （transient，不进信封序列化），客户端恒为 null。文件仍在 wire 上传输，此字段只是
     * "免业务二次解密"的便捷视图，不改变内存占用模型。
     */
    private transient byte[] plainContent;

    /** 获取方式：push（直传）/ pull（URL 拉取）/ refer（引用已有）。 */
    private FetchMode fetchMode;

    /** pull 模式必填：文件所在 URL（域名须在终端 upload-fetch-url-whitelist 白名单内）。 */
    private String url;

    /** pull 模式可选：下载令牌（随 X-Fetch-Token 头透传给源站；日志输出自动脱敏）。 */
    private String fetchToken;

    /** 备注。 */
    private String remark;

    /** 业务扩展字段。 */
    private Map<String, String> extra;
}
