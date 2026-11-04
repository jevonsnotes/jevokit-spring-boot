package io.github.jevonsnotes.jevokit.api.upload;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockPart;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.dto.FetchMode;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDetailDto;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JSecureUploadVerifier 纯单元测试（无 Spring 上下文）：元数据层边界（fileId 重复/空、
 * push 必填缺失、digestAlg 非法）、非 multipart 请求 fail-fast、无请求上下文 fail-fast、
 * refer 钩子存在/缺失的判定路径（正向链路与调包/数量防线由 @WebMvcTest 切片覆盖）。
 */
@DisplayName("上传校验器元数据层边界")
class JSecureUploadVerifierTest {

    private final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    private JSecureUploadVerifier verifier;
    private JevokitApiClientProperties client;

    @BeforeEach
    void setUp() {
        verifier = new JSecureUploadVerifier(beanFactory.getBeanProvider(JSecureUploadFileRefChecker.class));
        client = new JevokitApiClientProperties();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("合规 push 请求（含两个文件 part）通过")
    void validPushPasses() {
        mockMultipartRequest(filePart("file-1", "content-1"), filePart("file-2", "content-2"));
        assertThatCode(() -> verifier.verifyUpload(upload(pushEntry("file-1", "content-1"), pushEntry("file-2", "content-2")), client))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("fileId 重复必拒")
    void duplicateFileIdRejected() {
        mockMultipartRequest(filePart("file-1", "content-1"));
        assertThatThrownBy(() -> verifier.verifyUpload(
                        upload(pushEntry("file-1", "content-1"), pushEntry("file-1", "content-1")), client))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("文件标识重复");
    }

    @Test
    @DisplayName("fileId 空必拒")
    void blankFileIdRejected() {
        mockMultipartRequest(filePart("file-1", "content-1"));
        JSecureUploadFileDetailDto entry = pushEntry("file-1", "content-1");
        entry.setFileId(" ");
        assertThatThrownBy(() -> verifier.verifyUpload(upload(entry), client))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("fileId 不能为空");
    }

    @Test
    @DisplayName("push 缺 digest 必拒（必填完整性）")
    void pushWithoutDigestRejected() {
        mockMultipartRequest(filePart("file-1", "content-1"));
        JSecureUploadFileDetailDto entry = pushEntry("file-1", "content-1");
        entry.setDigest(null);
        assertThatThrownBy(() -> verifier.verifyUpload(upload(entry), client))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("缺少 digest");
    }

    @Test
    @DisplayName("push 缺 fileType/fileName/size 各自必拒；format 必拒仅当终端配置了白名单（未配置则放行）")
    void pushRequiredFieldsRejected() {
        mockMultipartRequest(filePart("file-1", "content-1"));
        // 随商榷点 2（无扩展名文件无法 push）修复：format 不再无条件必填——
        // 未配置 format 白名单的终端无校验需求、format 空放行；原"缺 format 必拒"断言随行为放宽更新
        for (String field : List.of("fileType", "fileName")) {
            JSecureUploadFileDetailDto entry = pushEntry("file-1", "content-1");
            switch (field) {
                case "fileType" -> entry.setFileType(null);
                case "fileName" -> entry.setFileName(null);
            }
            assertThatThrownBy(() -> verifier.verifyUpload(upload(entry), client))
                    .as("缺 %s 应拒绝", field)
                    .isInstanceOf(JSecureApiException.class)
                    .hasMessageContaining("缺少 " + field);
        }
        JSecureUploadFileDetailDto noSize = pushEntry("file-1", "content-1");
        noSize.setSize(null);
        assertThatThrownBy(() -> verifier.verifyUpload(upload(noSize), client))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("缺少 size");

        // 未配置白名单：format 空的 push（无扩展名文件）放行
        JSecureUploadFileDetailDto extensionLess = pushEntry("file-1", "content-1");
        extensionLess.setFormat("");
        assertThatCode(() -> verifier.verifyUpload(upload(extensionLess), client))
                .as("未配置 format 白名单的终端，无扩展名文件应放行")
                .doesNotThrowAnyException();

        // 配置了 format 白名单：format 空必拒（空串不可能命中白名单，无扩展名文件不可 push）
        client.setUploadFormatWhitelist(Map.of("invoice", List.of("pdf")));
        JSecureUploadFileDetailDto withWhitelist = pushEntry("file-1", "content-1");
        withWhitelist.setFormat("");
        assertThatThrownBy(() -> verifier.verifyUpload(upload(withWhitelist), client))
                .as("配置了白名单的终端，format 空无法比对必拒")
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("不在白名单");
    }

    @Test
    @DisplayName("size 与实际不符必拒（内容层预比对）")
    void sizeMismatchRejected() {
        mockMultipartRequest(filePart("file-1", "content-1"));
        JSecureUploadFileDetailDto entry = pushEntry("file-1", "content-1");
        entry.setSize(999L);
        assertThatThrownBy(() -> verifier.verifyUpload(upload(entry), client))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("大小与声明不符");
    }

    @Test
    @DisplayName("非 multipart 请求 + 上传 DTO 必拒（装配错误 fail-fast）")
    void nonMultipartRequestRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("application/json");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertThatThrownBy(() -> verifier.verifyUpload(upload(pushEntry("file-1", "content-1")), client))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("multipart");
    }

    @Test
    @DisplayName("无请求上下文必拒（verifyUpload 脱离请求线程手工调用时 fail-fast）")
    void missingRequestContextRejected() {
        assertThatThrownBy(() -> verifier.verifyUpload(upload(pushEntry("file-1", "content-1")), client))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("无法获取当前请求");
    }

    @Test
    @DisplayName("refer：钩子缺失必拒 / 钩子存在且文件存在通过")
    void referHookResolution() {
        mockMultipartRequest();
        JSecureUploadFileDetailDto referEntry = new JSecureUploadFileDetailDto();
        referEntry.setFileId("archived-1");
        referEntry.setFetchMode(FetchMode.refer);
        assertThatThrownBy(() -> verifier.verifyUpload(upload(referEntry), client))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("JSecureUploadFileRefChecker");

        beanFactory.registerSingleton("demoChecker", (JSecureUploadFileRefChecker) file -> Set.of("archived-1").contains(file.getFileId()));
        assertThatCode(() -> verifier.verifyUpload(upload(referEntry), client))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("pull：未配白名单必拒（fail-fast，不静默放行）")
    void pullWithoutWhitelistRejected() {
        mockMultipartRequest();
        JSecureUploadFileDetailDto pullEntry = new JSecureUploadFileDetailDto();
        pullEntry.setFileId("remote-1");
        pullEntry.setFetchMode(FetchMode.pull);
        pullEntry.setUrl("https://files.partner.com/a.pdf");
        assertThatThrownBy(() -> verifier.verifyUpload(upload(pullEntry), client))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("不在白名单");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 造 multipart 请求并放入 RequestContextHolder（文件 part 视需要附加）。 */
    private void mockMultipartRequest(MockPart... parts) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("multipart/form-data");
        request.setMethod("POST");
        for (MockPart part : parts) {
            request.addPart(part);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    /** 造文件 part（part 名 files，filename=fileId）。 */
    private MockPart filePart(String fileId, String content) {
        return new MockPart("files", fileId, content.getBytes(StandardCharsets.UTF_8), null);
    }

    /** 造合规 push 条目（sha256 摘要自动计算）。 */
    private JSecureUploadFileDetailDto pushEntry(String fileId, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        JSecureUploadFileDetailDto entry = new JSecureUploadFileDetailDto();
        entry.setFileId(fileId);
        entry.setFileType("invoice");
        entry.setFileName(fileId + ".pdf");
        entry.setFormat("pdf");
        entry.setSize((long) bytes.length);
        entry.setDigest(JSecureUploadUtil.digestHex(bytes, "sha256"));
        entry.setDigestAlg("sha256");
        entry.setFetchMode(FetchMode.push);
        return entry;
    }

    private JSecureUploadDto<Object, JSecureUploadFileDetailDto> upload(JSecureUploadFileDetailDto... files) {
        return new JSecureUploadDto<>("biz", List.of(files));
    }
}
