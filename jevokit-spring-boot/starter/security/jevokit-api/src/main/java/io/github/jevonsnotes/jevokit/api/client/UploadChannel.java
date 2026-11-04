package io.github.jevonsnotes.jevokit.api.client;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.BytesResource;
import cn.hutool.core.io.resource.StringResource;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResource;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import io.github.jevonsnotes.jevokit.api.callback.IUploadRequest;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.dto.FetchMode;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDetailDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadRequest;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;
import io.github.jevonsnotes.jevokit.api.upload.JSecureUploadUtil;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSecure 上传协议通道构建器（经 {@link JSecureClient#upload(String, Object, Class)} 获得）：
 * 封装"作为调用方"发起一次 multipart 安全上传的组装与执行——文件摘要清单生成
 * （size/format/digest 自动计算）、业务数据 + 清单整体 SM2 签名与 SM4 加密（meta part 加密信封）、
 * 协议头（client-id/timestamp/nonce/signature）与 push 文件 part 打包，及响应解密验签。
 *
 * <p>文件条目三通道（可混合）：{@link #add(File, String, String) add}（push 直传，自动算摘要）、
 * {@link #pull(String, String, Long, String) pull}（URL 拉取）、
 * {@link #refer(String) refer}（引用已有）、{@link #entry(JSecureUploadFileDetailDto, byte[]) entry}（全自定）。
 * <p>传输载体：{@code execute()} 空参走默认载体（hutool HttpRequest：协议头 headerMap +
 * meta part[application/json] + 逐文件 form "files" 且 filename=fileId）；
 * {@code execute(IUploadRequest)} 注入自定义传输载体回调，payload 即 {@link JSecureUploadRequest}。
 * <p>加密/签名配方与 JSON 通道（{@link JsonChannel}）同源（SM2 签名 + 按终端开关 SM4 加密 + 协议头签名），
 * 服务端解密适配器按同一配方还原。关键约束：push 条目按 add 顺序生成摘要清单，
 * fileId 请求内唯一（重复即抛错 fail-fast），且仅允许字母、数字与 {@code . _ -}
 * （长度上限 {@value JSecureUploadUtil#FILE_ID_MAX_LENGTH}）——fileId 即文件 part 的 filename，
 * 非法字符（引号/CRLF/中文等）在入口即抛 {@link JSecureApiException}，防 multipart 头走私与注入。
 */
public class UploadChannel<B, R> {

    /** 终端识别码。 */
    private final String clientId;

    /** 请求地址（服务端为 meta + files 双参数端点）。 */
    @Getter
    private final String url;

    /** 业务数据（泛型 B，任意 DTO 或 Map）。 */
    private final B businessData;

    /** 返回数据类型。 */
    @Getter
    private final Class<R> responseClass;

    /** 文件摘要清单（push/pull/refer 统一存放）。 */
    private final List<JSecureUploadFileDetailDto> fileEntries = new ArrayList<>();

    /** push 文件内容（fileId → 字节，即 wire part 实际字节）。 */
    private final Map<String, byte[]> pushContents = new LinkedHashMap<>();

    /**
     * add(..., encrypt=true) 暂存的待加密明文（fileId → 明文字节）——加密需要终端 SM4 密钥，
     * 延迟到 prepare 解析终端后执行；密文字节入 pushContents、size 回填条目。
     */
    private final Map<String, byte[]> pendingEncrypt = new LinkedHashMap<>();

    /** 交易号（不设则 prepare 时自动生成 UUID——服务端 transNo 为 @NotBlank 必填）。 */
    private String transNo;

    /** 交易类型（服务端 @JSecureApi 声明了 transType 限定时必填）。 */
    private String transType;

    /**
     * 摘要算法（add 自动计算的算法与 pull 声明的算法；缺省 sha256）——
     * 服务端终端配置 upload-digest-algorithms 限定范围时按其取值（如 sm3）。
     */
    private String digestAlg = JSecureUploadUtil.DIGEST_ALG_SHA256;

    /** 终端配置（prepare 时按 clientId 匹配）。 */
    private JevokitApiClientProperties client;

    /** 组装产物（prepare 时生成）：meta part + 协议头 + 文件 part 的外发要素全集。 */
    @Getter
    private JSecureUploadRequest request;

    /** prepare 标记（已组装则幂等跳过，execute 自动补做）。 */
    @Getter
    private boolean prepared;

    UploadChannel(String clientId, String url, B businessData, Class<R> responseClass) {
        Assert.notNull(businessData, () -> new JSecureApiException("上传业务数据不能为空"));
        this.clientId = clientId;
        this.url = url;
        this.businessData = businessData;
        this.responseClass = responseClass;
    }

    /**
     * 设置交易号（不设则 prepare 时自动生成 UUID——服务端 transNo 为 @NotBlank 必填，
     * 由客户端兜底生成保证报文恒可过校验）。
     *
     * @param transNo 交易号
     * @return 本构建器（链式）
     */
    public UploadChannel<B, R> transNo(String transNo) {
        this.transNo = transNo;
        return this;
    }

    /**
     * 设置交易类型（服务端声明限定时必填）。
     *
     * @param transType 交易类型
     * @return 本构建器（链式）
     */
    public UploadChannel<B, R> transType(String transType) {
        this.transType = transType;
        return this;
    }

    /**
     * 设置摘要算法（sha256/sm3/md5）：影响其后 add() 自动计算的摘要与 pull() 声明的算法；
     * 不设则缺省 sha256。调用方须按服务端终端 upload-digest-algorithms 允许范围自行取值
     * （本客户端不读服务端配置，范围外算法将等服务端拒绝）；已有条目后再调用会 fail-fast
     * （防"先 add 后改算法"生成算法口径不一致的报文），须先 digestAlg() 再 add()/pull()。
     *
     * @param digestAlg 摘要算法名（空白归一 sha256，协议外算法名 fail-fast）
     * @return 本构建器（链式）
     */
    public UploadChannel<B, R> digestAlg(String digestAlg) {
        Assert.isTrue(fileEntries.isEmpty(), () -> new JSecureApiException(
                "digestAlg() 须在 add()/pull()/entry() 之前调用（已有条目按原算法生成摘要）"));
        this.digestAlg = JSecureUploadUtil.normalizeDigestAlg(digestAlg);
        return this;
    }

    /**
     * 追加 push 文件（自动计算 size/format/digest，算法由 digestAlg() 设定，缺省 sha256）。
     *
     * @param file     本地文件
     * @param fileId   请求内唯一标识（即 multipart part 的 filename，仅允许字母/数字与 . _ -）
     * @param fileType 业务类型（服务端 format 白名单按它配置）
     * @return 本构建器（链式）
     */
    public UploadChannel<B, R> add(File file, String fileId, String fileType) {
        return add(file, fileId, fileType, file == null ? null : file.getName());
    }

    /**
     * 追加 push 文件（自动计算 size/format/digest，算法由 digestAlg() 设定，缺省 sha256）。
     *
     * @param file     本地文件
     * @param fileId   请求内唯一标识（仅允许字母/数字与 . _ -）
     * @param fileType 业务类型
     * @param fileName 展示用原始文件名（仅展示，服务端永不落盘）
     * @return 本构建器（链式）
     */
    public UploadChannel<B, R> add(File file, String fileId, String fileType, String fileName) {
        return add(file, fileId, fileType, fileName, false);
    }

    /**
     * 追加 push 文件（本地文件，可选括号内自动加密，语义同 {@link #add(byte[], String, String, String, boolean)}）。
     *
     * @param file     本地文件
     * @param fileId   请求内唯一标识
     * @param fileType 业务类型
     * @param fileName 展示用原始文件名
     * @param encrypt  是否内部加密
     * @return 本构建器（链式）
     */
    public UploadChannel<B, R> add(File file, String fileId, String fileType, String fileName, boolean encrypt) {
        Assert.notNull(file, () -> new JSecureApiException("上传文件不能为空：" + fileId));
        try {
            return add(Files.readAllBytes(file.toPath()), fileId, fileType, fileName, encrypt);
        } catch (IOException e) {
            throw new JSecureApiException("读取上传文件失败：" + file.getPath(), e);
        }
    }

    /**
     * 追加 push 文件（自动计算 size/format/digest，算法由 digestAlg() 设定，缺省 sha256）。
     *
     * @param content  文件内容
     * @param fileId   请求内唯一标识（仅允许字母/数字与 . _ -）
     * @param fileType 业务类型
     * @return 本构建器（链式）
     */
    public UploadChannel<B, R> add(byte[] content, String fileId, String fileType) {
        return add(content, fileId, fileType, null);
    }

    /**
     * 追加 push 文件（自动计算 size/format/digest，算法由 digestAlg() 设定（缺省 sha256）；format 取 fileName 扩展名，
     * 无扩展名时留空交服务端白名单判定）。
     *
     * @param content  文件内容
     * @param fileId   请求内唯一标识（仅允许字母/数字与 . _ -）
     * @param fileType 业务类型
     * @param fileName 展示用原始文件名（兼作 format 推断来源）
     * @return 本构建器（链式）
     */
    public UploadChannel<B, R> add(byte[] content, String fileId, String fileType, String fileName) {
        return add(content, fileId, fileType, fileName, false);
    }

    /**
     * 追加 push 文件（可选括号内自动加密）：encrypt=true 时传入<b>明文字节</b>，内部以终端
     * SM4 密钥 SM4-CBC 加密（随机 IV 前缀）后作为文件 part 字节；digest 按<b>加密前明文</b>
     * 计算（服务端解密后复核），size 回填密文字节数（即 wire 上实际大小），条目标志
     * encrypted=true（服务端校验链无感解密，业务直接从条目拿明文）。
     *
     * @param content  文件内容（encrypt=true 时为明文；false 时原样作为 part 字节）
     * @param fileId   请求内唯一标识（仅允许字母/数字与 . _ -）
     * @param fileType 业务类型
     * @param fileName 展示用原始文件名（兼作 format 推断来源）
     * @param encrypt  是否内部加密（SM4-CBC，密钥共用终端 SM4 密钥）
     * @return 本构建器（链式）
     */
    public UploadChannel<B, R> add(byte[] content, String fileId, String fileType, String fileName, boolean encrypt) {
        Assert.notNull(content, () -> new JSecureApiException("上传文件内容不能为空：" + fileId));
        JSecureUploadFileDetailDto entry = new JSecureUploadFileDetailDto();
        entry.setFileId(fileId);
        entry.setFileType(fileType);
        entry.setFileName(fileName);
        entry.setFormat(inferFormat(fileName));
        entry.setDigest(JSecureUploadUtil.digestHex(content, digestAlg));   // 加密条目也按明文算
        entry.setDigestAlg(digestAlg);
        entry.setEncrypted(encrypt);
        entry.setFetchMode(FetchMode.push);
        if (encrypt) {
            // size 为密文字节数（IV 16B + padding），prepare 加密后回填；明文暂存延后加密
            pendingEncrypt.put(fileId, content);
            return entry(entry, null);
        }
        entry.setSize((long) content.length);
        return entry(entry, content);
    }

    /**
     * 追加 pull 文件（URL 拉取，服务端域名白名单准入；size/digest 由调用方如实声明，
     * 服务端拉取时复核）。
     *
     * @param fileId 请求内唯一标识（仅允许字母/数字与 . _ -）
     * @param url    文件地址（域名须在服务端终端 upload-fetch-url-whitelist 白名单内）
     * @param size   文件字节数（可空）
     * @param digest 文件摘要（hex，可空；按 digestAlg() 设定的算法计算，声明后服务端拉取复核）
     * @return 本构建器（链式）
     */
    public UploadChannel<B, R> pull(String fileId, String url, Long size, String digest) {
        return pull(fileId, url, size, digest, null, null);
    }

    /**
     * 追加 pull 文件（同 {@link #pull(String, String, Long, String)}，可同时声明 fileType/format）。
     *
     * @param fileId   请求内唯一标识（仅允许字母/数字与 . _ -）
     * @param url      文件地址
     * @param size     文件字节数（可空）
     * @param digest   文件摘要（hex，可空）
     * @param fileType 业务类型
     * @param format   文件格式
     * @return 本构建器（链式）
     */
    public UploadChannel<B, R> pull(String fileId, String url, Long size, String digest,
                                    String fileType, String format) {
        JSecureUploadFileDetailDto entry = new JSecureUploadFileDetailDto();
        entry.setFileId(fileId);
        entry.setFileType(fileType);
        entry.setFormat(format);
        entry.setSize(size);
        entry.setDigest(digest);
        entry.setDigestAlg(digestAlg);
        entry.setFetchMode(FetchMode.pull);
        entry.setUrl(url);
        return entry(entry, null);
    }

    /**
     * 追加 refer 文件（引用服务端系统内已存在文件，由 JSecureUploadFileRefChecker 钩子校验存在性）。
     *
     * @param fileId 请求内唯一标识（业务侧引用键，仅允许字母/数字与 . _ -）
     * @return 本构建器（链式）
     */
    public UploadChannel<B, R> refer(String fileId) {
        JSecureUploadFileDetailDto entry = new JSecureUploadFileDetailDto();
        entry.setFileId(fileId);
        entry.setFetchMode(FetchMode.refer);
        return entry(entry, null);
    }

    /**
     * 追加全自定条目（escape hatch：sm3 摘要、extra、fetchToken 等全字段自控）；
     * content 非空即按 push 文件 part 打包。fileId 走字符集白名单
     * （仅允许字母、数字与 . _ -，长度 1-{@value JSecureUploadUtil#FILE_ID_MAX_LENGTH}）：
     * 含引号/CRLF/中文等非法字符即抛异常 fail-fast，防 multipart filename 走私与 part 头注入。
     *
     * @param fileEntry 完整文件元数据条目（fetchMode 自行设置）
     * @param content   push 内容（fetchMode=pull/refer 时传 null）
     * @return 本构建器（链式）
     * @throws JSecureApiException fileId 非法（字符集/长度）、为空或重复时抛出
     */
    public UploadChannel<B, R> entry(JSecureUploadFileDetailDto fileEntry, byte[] content) {
        Assert.notNull(fileEntry, () -> new JSecureApiException("文件条目不能为空"));
        Assert.notBlank(fileEntry.getFileId(), () -> new JSecureApiException("文件标识 fileId 不能为空"));
        // fileId 即 multipart 文件 part 的 filename（对齐键）：字符集白名单收紧，防走私/注入（与服务端同口径）
        Assert.isTrue(JSecureUploadUtil.isFileIdAllowed(fileEntry.getFileId()), () -> new JSecureApiException(
                "文件标识 fileId 非法：仅允许" + JSecureUploadUtil.FILE_ID_ALLOWED_CHARS_DESC
                        + "，长度 1-" + JSecureUploadUtil.FILE_ID_MAX_LENGTH
                        + "（当前长度 " + fileEntry.getFileId().length() + "）"));
        boolean duplicated = fileEntries.stream()
                .anyMatch(existing -> existing.getFileId().equals(fileEntry.getFileId()));
        Assert.isFalse(duplicated, () -> new JSecureApiException("文件标识重复：" + fileEntry.getFileId()));
        fileEntries.add(fileEntry);
        if (content != null) {
            pushContents.put(fileEntry.getFileId(), content);
        }
        return this;
    }

    /**
     * 组装上传请求（幂等：已组装则原样返回）：业务数据 + 摘要清单整体签名加密为 meta part 信封，
     * 生成协议头与文件 part。加密/签名配方与 JSON 通道同源。
     *
     * @return 本构建器（prepared 标记置真，产物经 {@link #getRequest()} 取用）
     * @throws JSecureApiException 终端配置缺失或签名加密失败时抛出
     */
    public UploadChannel<B, R> prepare() {
        if (prepared) {
            return this;
        }
        client = JSecureApiUtil.getClient(clientId);
        // 自动加密条目：终端密钥就绪后加密暂存明文，密文入 part、size 回填（信封序列化前完成）
        pendingEncrypt.forEach((fileId, plain) -> {
            byte[] cipher = io.github.jevonsnotes.jevokit.core.util.SecurityUtil.sm4cbcEncrypt(plain, client.getSecretKeySm4());
            fileEntries.stream()
                    .filter(e -> fileId.equals(e.getFileId()))
                    .findFirst()
                    .ifPresent(e -> e.setSize((long) cipher.length));
            pushContents.put(fileId, cipher);
        });
        // transNo 不设则自动生成：服务端 @NotBlank 无条件必填，客户端兜底保证报文恒可过校验
        if (StrUtil.isBlank(transNo)) {
            transNo = UUID.randomUUID().toString();
        }

        @SuppressWarnings("unchecked")
        JSecureUploadDto<B, JSecureUploadFileDetailDto> upload =
                new JSecureUploadDto<>(businessData, (List<JSecureUploadFileDetailDto>) (List<?>) fileEntries);
        JSecureRequest<B> envelope = new JSecureRequest<>();
        envelope.setTransNo(transNo);
        envelope.setTransType(transType);
        JSecureApiUtil.sealRequest(envelope, JSON.toJSONString(upload), client);

        List<JSecureUploadRequest.FilePart> fileParts = new ArrayList<>(pushContents.size());
        pushContents.forEach((fileId, content) ->
                fileParts.add(new JSecureUploadRequest.FilePart(fileId, content, "application/octet-stream")));
        request = new JSecureUploadRequest(JSON.toJSONString(envelope),
                JSecureApiUtil.buildProtocolHeaders(client), fileParts);
        prepared = true;
        return this;
    }

    /**
     * 默认载体发送（hutool multipart）：协议头挂根请求，meta part（name=meta、无 filename、
     * Content-Type=application/json）装加密信封，push 文件逐个 form "files" 且 filename=fileId，
     * 响应走统一解密验签。非 2xx 状态码（网关 404/500 错误页）在此即抛
     * {@link JSecureApiException}（传输层失败），不把错误页当响应体送入协议解析。
     *
     * @return 已解密验签的协议响应
     * @throws JSecureApiException 发送失败、HTTP 非 2xx、响应解析失败或验签失败时抛出
     */
    public JSecureResponse<R> execute() {
        return execute((url, payload) -> {
            try {
                HttpRequest httpRequest = HttpRequest.post(url).headerMap(payload.getHeaders(), true);
                // meta part：Resource 名置空（无 filename 属性，服务端按"非文件 part"排除），
                // 经 HttpResource 显式声明 application/json（服务端按它路由消息转换器）
                httpRequest.form("meta", new HttpResource(
                        new StringResource(payload.getMetaJson(), null, StandardCharsets.UTF_8), "application/json"));
                for (JSecureUploadRequest.FilePart file : payload.getFiles()) {
                    httpRequest.form(file.partName(), new HttpResource(
                            new BytesResource(file.getContent(), file.getFileId()), file.getContentType()));
                }
                HttpResponse response = httpRequest.execute();
                // hutool 对非 2xx 不抛异常（body 原样返回，404 还会捏造错误文本），此处显式判定传输层失败
                JSecureApiUtil.assertHttpSuccess(response.getStatus(), url);
                return response.body();
            } catch (JSecureApiException e) {
                throw e;
            } catch (Exception e) {
                throw new JSecureApiException("JSecure 上传请求发送失败：" + url, e);
            }
        });
    }

    /**
     * 发起上传并处理响应：未 prepare 自动补做；经 {@link IUploadRequest} 发送
     * （payload 三要素：metaJson / headers / files），响应解密（view）、反序列化（target）
     * 并按终端开关验签（与 JSON 通道同源处理）。
     *
     * @param callback 传输载体回调（任意 HTTP 客户端实现，协议头须原样透传）
     * @return 已解密验签的协议响应
     * @throws JSecureApiException 响应验签失败时抛出（不返回未验签数据）
     */
    public JSecureResponse<R> execute(IUploadRequest callback) {
        prepare();
        String res = callback.call(url, request);
        return JSecureApiUtil.handleResponse(res, client, responseClass);
    }

    /**
     * 从文件名推断格式（小写扩展名；无扩展名返回空串）。
     */
    private String inferFormat(String fileName) {
        String extension = FileUtil.extName(fileName);
        return extension == null ? StrUtil.EMPTY : extension.toLowerCase();
    }
}
