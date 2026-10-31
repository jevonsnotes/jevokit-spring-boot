package io.github.jevonsnotes.jevokit.api.upload;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.util.WebUtils;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.dto.FetchMode;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDto;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 上传协议无感校验器：在解密 advice 的 afterBodyRead 阶段对 {@link JSecureUploadDto} 业务体
 * 做分层校验（成本递增，任何一层不通过抛 {@link JSecureApiException} 走协议信封出口）：
 * <ol>
 *   <li><b>元数据层</b>（不碰文件字节）：fileId 唯一非空且字符集白名单
 *       （仅允许字母、数字与 {@code . _ -}，长度上限 {@value JSecureUploadUtil#FILE_ID_MAX_LENGTH}，
 *       防绕过客户端校验的 filename 走私/CRLF 头注入）、digestAlg 合法（sha256/sm3）、
 *       format 终端白名单（fail-closed；未配置白名单的终端不校验 format，无扩展名文件可 push）、
 *       size 终端上限、push 必填完整（fileId/fileType/fileName/size/digest）、
 *       push 条目数 = 文件 part 数（多余/缺少双向都拒）、meta part 不重复（同名 &gt;1 即拒，
 *       防 first-wins 静默歧义）；</li>
 *   <li><b>内容层</b>（仅 push）：按 filename=fileId 对齐文件 part → 比对 size → 流式摘要比对
 *       （调包——两个文件互换 fileId、篡改字节——均拒）；</li>
 *   <li><b>pull</b>：元数据层 + url 域名白名单（SSRF 硬防线，白名单未配置即拒）；</li>
 *   <li><b>refer</b>：元数据层 + 业务钩子 {@link JSecureUploadFileRefChecker} 存在性校验
 *       （容器无钩子 Bean 而出现 refer 条目即拒）。</li>
 * </ol>
 * 边界规则：target 是上传 DTO 但请求非 multipart（装配错误）→ 拒。
 * {@link #verifyUpload} 保持 public：正常链路由 advice 无感调用，业务也可单测/极端场景手工复核。
 */
@Slf4j
@RequiredArgsConstructor
public class JSecureUploadVerifier {

    /** multipart 文件 part 的对齐键约定：part 名固定 files，filename = fileId。 */
    public static final String FILE_PART_NAME = "files";

    /** multipart 加密信封 part 名约定（客户端默认载体与服务端 @RequestPart 同名）。 */
    public static final String META_PART_NAME = "meta";

    private final ObjectProvider<JSecureUploadFileRefChecker> refCheckerProvider;

    /**
     * 上传业务体完整性校验（分层规则见类注释）。
     *
     * @param upload 解密后的上传业务体（JSecureRequest.target）
     * @param client 当前终端配置（白名单/上限均终端级）
     * @throws JSecureApiException 任一校验不通过时抛出（消息面向终端提示，不泄露内部细节）
     */
    public void verifyUpload(JSecureUploadDto<?, ?> upload, JevokitApiClientProperties client) {
        Assert.notNull(upload, () -> new JSecureApiException("上传数据不能为空"));
        List<? extends JSecureUploadFileDto> files =
                upload.getFiles() == null ? List.of() : upload.getFiles();

        verifyMetadata(files, client);

        HttpServletRequest request = JSecureApiUtil.getCurrentRequest()
                .orElseThrow(() -> new JSecureApiException("无法获取当前请求，上传校验失败"));
        Assert.isTrue(isMultipart(request), () -> new JSecureApiException(
                "上传接口必须以 multipart 请求（meta 加密信封 + 文件 part），当前请求非 multipart"));

        verifyNoDuplicateMetaPart(request);
        verifyPushContents(files, collectFileParts(request));
    }

    /**
     * 元数据层校验（不碰文件字节）。
     */
    private void verifyMetadata(List<? extends JSecureUploadFileDto> files, JevokitApiClientProperties client) {
        long uploadMaxSize = client.getUploadMaxSize();
        Map<String, List<String>> formatWhitelist = client.getUploadFormatWhitelist();
        boolean formatWhitelistConfigured = formatWhitelist != null && !formatWhitelist.isEmpty();
        Set<String> seenFileIds = new HashSet<>();
        for (JSecureUploadFileDto file : files) {
            Assert.notNull(file, () -> new JSecureApiException("文件条目不能为空"));
            String fileId = file.getFileId();
            Assert.notBlank(fileId, () -> new JSecureApiException("文件标识 fileId 不能为空"));
            Assert.isTrue(seenFileIds.add(fileId), () -> new JSecureApiException("文件标识重复：" + fileId));
            // fileId 字符集白名单（与客户端入口同口径）：fileId 即文件 part 的 filename，
            // 含引号/CRLF 等非法字符的报文直接拒——防绕过客户端校验的自定义载体走私/注入 multipart 头
            Assert.isTrue(JSecureUploadUtil.isFileIdAllowed(fileId), () -> new JSecureApiException(
                    "文件标识 fileId 非法（仅允许" + JSecureUploadUtil.FILE_ID_ALLOWED_CHARS_DESC
                            + "，长度 1-" + JSecureUploadUtil.FILE_ID_MAX_LENGTH + "）："
                            + StrUtil.maxLength(fileId, 32)));
            // digestAlg 合法性（空白归一 sha256）
            JSecureUploadUtil.normalizeDigestAlg(file.getDigestAlg());
            // format 终端白名单（配置后 fail-closed：fileType 必须有映射且 format 在列表内）。
            // 适用范围：push 必查（format 必填——含 format 为空的情形，空串不可能命中列表即拒，
            // 无扩展名文件在配置了白名单的终端不可 push）；pull 声明了 fileType+format 才查（简式 pull 可省略）；
            // refer 不查（文件在首次上传时已校验过格式，引用条目无需重复携带）。
            // 未配置白名单的终端不校验 format（无校验需求，无扩展名文件如 README 可 push，format 留空不参与落盘/解析）。
            if (formatWhitelistConfigured && formatWhitelistApplies(file)) {
                List<String> allowedFormats = formatWhitelist.get(StrUtil.trimToEmpty(file.getFileType()));
                Assert.isTrue(allowedFormats != null && !allowedFormats.isEmpty(),
                        () -> new JSecureApiException("文件类型未在上传白名单配置：" + file.getFileType()));
                Assert.isTrue(allowedFormats.contains(StrUtil.trimToEmpty(file.getFormat())),
                        () -> new JSecureApiException("文件格式不在白名单：" + file.getFormat()));
            }
            FetchMode fetchMode = file.getFetchMode();
            Assert.notNull(fetchMode, () -> new JSecureApiException("文件获取方式 fetchMode 不能为空：" + fileId));
            switch (fetchMode) {
                case push -> {
                    Assert.notBlank(file.getFileType(), () -> new JSecureApiException("push 文件缺少 fileType：" + fileId));
                    Assert.notBlank(file.getFileName(), () -> new JSecureApiException("push 文件缺少 fileName：" + fileId));
                    // format 不在此强制必填：配置了白名单的终端由上方白名单校验兜住（format 空必拒），
                    // 未配置白名单的终端放行（无扩展名文件可 push）
                    Assert.notNull(file.getSize(), () -> new JSecureApiException("push 文件缺少 size：" + fileId));
                    Assert.notBlank(file.getDigest(), () -> new JSecureApiException("push 文件缺少 digest：" + fileId));
                    Assert.isTrue(file.getSize() >= 0, () -> new JSecureApiException("push 文件 size 非法：" + fileId));
                    Assert.isTrue(file.getSize() <= uploadMaxSize, () -> new JSecureApiException(
                            "文件超出大小上限（" + uploadMaxSize + " 字节）：" + fileId));
                }
                case pull -> {
                    Assert.notBlank(file.getUrl(), () -> new JSecureApiException("pull 文件缺少 url：" + fileId));
                    Assert.isTrue(JSecureUploadUtil.isUrlAllowed(client.getFetchUrlWhitelist(), file.getUrl()),
                            () -> new JSecureApiException("pull 文件地址不在白名单：" + JSecureUploadLogMasker.maskUrl(file.getUrl())));
                    Assert.isTrue(file.getSize() == null || (file.getSize() >= 0 && file.getSize() <= uploadMaxSize),
                            () -> new JSecureApiException("pull 文件 size 非法或超出上限：" + fileId));
                }
                case refer -> {
                    JSecureUploadFileRefChecker checker = refCheckerProvider.getIfAvailable();
                    Assert.notNull(checker, () -> new JSecureApiException(
                            "refer 模式需要注册 JSecureUploadFileRefChecker 校验钩子（容器中未发现实现）"));
                    Assert.isTrue(checker.exists(file), () -> new JSecureApiException("refer 文件不存在：" + fileId));
                }
                default -> throw new JSecureApiException("不支持的文件获取方式：" + fetchMode);
            }
        }
    }

    /**
     * 判定该条目是否适用 format 白名单：push 必查；pull 声明了 fileType+format 才查；refer 不查。
     */
    private boolean formatWhitelistApplies(JSecureUploadFileDto file) {
        if (FetchMode.refer.equals(file.getFetchMode())) {
            return false;
        }
        if (FetchMode.pull.equals(file.getFetchMode())) {
            return StrUtil.isNotBlank(file.getFileType()) && StrUtil.isNotBlank(file.getFormat());
        }
        return true;
    }

    /**
     * push 内容层校验：条目数与文件 part 双向对齐，按 filename=fileId 找 part，比 size、流式比摘要。
     */
    private void verifyPushContents(List<? extends JSecureUploadFileDto> files, List<UploadFilePart> fileParts) {
        List<? extends JSecureUploadFileDto> pushEntries = files.stream()
                .filter(file -> FetchMode.push.equals(file.getFetchMode()))
                .toList();
        Assert.isTrue(pushEntries.size() == fileParts.size(), () -> new JSecureApiException(String.format(
                "push 文件数(%d)与文件 part 数(%d)不一致", pushEntries.size(), fileParts.size())));

        Map<String, UploadFilePart> partsByFileId = new LinkedHashMap<>();
        for (UploadFilePart part : fileParts) {
            Assert.isFalse(partsByFileId.containsKey(part.fileName()),
                    () -> new JSecureApiException("文件 part 文件名重复：" + part.fileName()));
            partsByFileId.put(part.fileName(), part);
        }
        for (JSecureUploadFileDto entry : pushEntries) {
            UploadFilePart part = partsByFileId.get(entry.getFileId());
            Assert.notNull(part, () -> new JSecureApiException("缺少文件 part（filename=%s）".formatted(entry.getFileId())));
            Assert.isTrue(part.size() == entry.getSize(), () -> new JSecureApiException(
                    "文件大小与声明不符（filename=%s，声明 %d，实际 %d）".formatted(entry.getFileId(), entry.getSize(), part.size())));
            String actualDigest;
            try (InputStream in = part.openStream()) {
                actualDigest = JSecureUploadUtil.digestHex(in, entry.getDigestAlg());
            } catch (IOException e) {
                throw new JSecureApiException("文件读取失败（filename=" + entry.getFileId() + "）：" + e.getMessage(), e);
            }
            Assert.isTrue(actualDigest.equalsIgnoreCase(StrUtil.trimToEmpty(entry.getDigest())),
                    () -> new JSecureApiException("文件摘要校验失败（filename=" + entry.getFileId() + "）"));
        }
    }

    /**
     * 收集当前请求全部文件 part（filename 非空即文件 part；meta part 无 filename 天然排除）。
     * 兼容三类请求形态并按 (part 名, filename) 去重合并：
     * <ul>
     *   <li>原生容器：根请求 getParts()（容器解析的文件 part）；</li>
     *   <li>Spring 包装/MockMvc：解包包装链（含 XSS/审计等第三方 Filter 的 RequestWrapper）取
     *       MultipartHttpServletRequest 的 multiFileMap——MockMvc 下 .file() 附加的文件只在此处可见；</li>
     *   <li>二者共存时去重防双计数。</li>
     * </ul>
     */
    private List<UploadFilePart> collectFileParts(HttpServletRequest request) {
        Map<String, UploadFilePart> merged = new LinkedHashMap<>();
        try {
            for (Part part : request.getParts()) {
                if (part.getSubmittedFileName() != null) {
                    merged.putIfAbsent(part.getName() + "\n" + part.getSubmittedFileName(), new PartAdapter(part));
                }
            }
        } catch (IllegalStateException | IOException | jakarta.servlet.ServletException e) {
            throw new JSecureApiException("文件 part 读取失败：" + e.getMessage(), e);
        }
        // 沿 ServletRequestWrapper 链解包（防第三方 Filter 包装后丢失 MultipartHttpServletRequest 形态）
        MultipartHttpServletRequest multipartRequest =
                WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
        if (multipartRequest != null) {
            for (List<MultipartFile> multipartFiles : multipartRequest.getMultiFileMap().values()) {
                for (MultipartFile multipartFile : multipartFiles) {
                    merged.putIfAbsent(multipartFile.getName() + "\n" + multipartFile.getOriginalFilename(),
                            new MultipartFileAdapter(multipartFile));
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 判定请求是否 multipart（Content-Type 以 multipart/ 开头）。
     */
    private boolean isMultipart(HttpServletRequest request) {
        return Optional.ofNullable(request.getContentType())
                .map(contentType -> contentType.toLowerCase().startsWith("multipart/"))
                .orElse(false);
    }

    /**
     * meta part 重复检测（歧义报文拒绝）：Spring {@code @RequestPart} 对同名 part 取第一个、
     * 第二个会被无声忽略（first-wins），属于静默歧义——安全组件不受理不可确信的报文，
     * 同名 meta part 数量 &gt; 1 即拒绝。
     */
    private void verifyNoDuplicateMetaPart(HttpServletRequest request) {
        int metaPartCount = 0;
        try {
            for (Part part : request.getParts()) {
                if (META_PART_NAME.equals(part.getName())) {
                    metaPartCount++;
                }
            }
        } catch (IllegalStateException | IOException | jakarta.servlet.ServletException e) {
            throw new JSecureApiException("文件 part 读取失败：" + e.getMessage(), e);
        }
        if (metaPartCount > 1) {
            throw new JSecureApiException("meta part 重复（" + metaPartCount + " 个同名 part），歧义报文不予受理");
        }
    }

    /** 文件 part 统一视图（jakarta Part 与 Spring MultipartFile 的共同抽象）。 */
    private interface UploadFilePart {
        String partName();

        String fileName();

        long size();

        InputStream openStream() throws IOException;
    }

    /** 原生 Part 适配。 */
    private record PartAdapter(Part part) implements UploadFilePart {
        @Override
        public String partName() {
            return part.getName();
        }

        @Override
        public String fileName() {
            return part.getSubmittedFileName();
        }

        @Override
        public long size() {
            return part.getSize();
        }

        @Override
        public InputStream openStream() throws IOException {
            return part.getInputStream();
        }
    }

    /** Spring MultipartFile 适配。 */
    private record MultipartFileAdapter(MultipartFile file) implements UploadFilePart {
        @Override
        public String partName() {
            return file.getName();
        }

        @Override
        public String fileName() {
            return file.getOriginalFilename();
        }

        @Override
        public long size() {
            return file.getSize();
        }

        @Override
        public InputStream openStream() throws IOException {
            return file.getInputStream();
        }
    }
}
