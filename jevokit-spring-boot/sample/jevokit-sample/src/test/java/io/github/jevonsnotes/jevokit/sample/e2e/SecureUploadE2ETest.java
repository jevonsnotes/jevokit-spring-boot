package io.github.jevonsnotes.jevokit.sample.e2e;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import io.github.jevonsnotes.jevokit.api.client.JSecureClient;
import io.github.jevonsnotes.jevokit.api.client.UploadChannel;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiProperties;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadRequest;
import io.github.jevonsnotes.jevokit.sample.JevokitSampleApplication;
import io.github.jevonsnotes.jevokit.sample.vo.UploadOrderVo;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * JSecure 文件上传 E2E（sample 应用全上下文 + MockMvc multipart）：
 * push 正向（format 白名单内 pdf/jpg）、调包必拒、丢文件必拒、refer 钩子受理、pull 域名白名单内受理。
 *
 * <p>环境同 {@link SecureApiE2ETest}：Redis 在位 + H2 内存自给（local profile）。
 * 客户端报文由 JSecureClient 上传通道真实构造（协议头签名 + SM4 加密信封 + SM2 体签名 +
 * 摘要清单自动生成），负路径仅定点篡改 multipart 组装。
 */
@Slf4j
@ActiveProfiles("local")
@DisplayName("SecureUpload 端到端")
@SpringBootTest(classes = JevokitSampleApplication.class)
@AutoConfigureMockMvc
class SecureUploadE2ETest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JevokitApiProperties apiProperties;

    private JevokitApiClientProperties client;

    @BeforeEach
    void prepareDemoClient() {
        // 与 SecureApiE2ETest 同源：演示 secret demo-sm4-secret 非 16 字节（SM4 硬约束），
        // 测试内换 16 字节演示密钥并同步头签名密钥；IP 准入并入回环（MockMvc 下取本机回环）
        client = apiProperties.getClient().stream()
                .filter(c -> "client-demo-1".equals(c.getId()))
                .findFirst().orElseThrow();
        client.setSecretKeySm4("DemoSm4Key123456");
        client.setSecretKey("DemoSm4Key123456");
        if (!client.getAllowIp().contains("127.0.0.1")) {
            client.getAllowIp().add("127.0.0.1");
        }
    }

    @Test
    @DisplayName("push 正向：发票+身份证双文件（白名单内 pdf/jpg）全链路受理，code=0")
    void pushHappyPathAccepted() throws Exception {
        JSecureUploadRequest request = standardPushRequest();
        perform(request)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("调包必拒：两文件 filename 互换，code=999 摘要校验失败")
    void swappedPartsRejected() throws Exception {
        JSecureUploadRequest request = standardPushRequest();
        MockMultipartHttpServletRequestBuilder builder = multipartSkeleton(request);
        // 互换 filename：file-1 的 part 携带 file-2 的内容（两文件等长，越过 size 预检、由摘要层坐实调包）
        builder.file(new org.springframework.mock.web.MockMultipartFile("files",
                request.getFiles().get(0).getFileId(), "application/octet-stream", request.getFiles().get(1).getContent()));
        builder.file(new org.springframework.mock.web.MockMultipartFile("files",
                request.getFiles().get(1).getFileId(), "application/octet-stream", request.getFiles().get(0).getContent()));
        mockMvc.perform(builder)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("摘要校验失败")));
    }

    @Test
    @DisplayName("丢文件必拒：申报两个文件只发一个 part，code=999 数量不一致")
    void missingPartRejected() throws Exception {
        JSecureUploadRequest request = standardPushRequest();
        MockMultipartHttpServletRequestBuilder builder = multipartSkeleton(request);
        builder.file(new org.springframework.mock.web.MockMultipartFile("files",
                request.getFiles().get(0).getFileId(), "application/octet-stream", request.getFiles().get(0).getContent()));
        mockMvc.perform(builder)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("不一致")));
    }

    @Test
    @DisplayName("refer 受理：archived-001 经 DemoUploadRefChecker 判定存在，code=0")
    void referEntryAccepted() throws Exception {
        JSecureUploadRequest request = upload()
                .transNo(UUID.randomUUID().toString())
                .transType("T030422")
                .refer("archived-001")
                .prepare().getRequest();
        perform(request)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("pull 受理：127.0.0.1 在 fetch-url-whitelist 内，元数据层通过 code=0（下载时机属业务）")
    void pullEntryAccepted() throws Exception {
        JSecureUploadRequest request = upload()
                .transNo(UUID.randomUUID().toString())
                .transType("T030422")
                .pull("remote-1", "http://127.0.0.1:18080/demo-files/invoice.pdf", 8L, null, "invoice", "pdf")
                .prepare().getRequest();
        perform(request)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 造一个上传通道（按演示终端密钥组装，本类只取组装产物不发默认载体）。 */
    private UploadChannel<UploadOrderVo, Void> upload() {
        return JSecureClient.of(client.getId()).upload("/secure/upload", order(), Void.class);
    }

    private UploadOrderVo order() {
        return UploadOrderVo.builder().orderNo("BX-2026-0001").amount(199.00).submitter("E10001").build();
    }

    /** 标准正向请求：发票（pdf）+ 身份证（jpg），均在终端 format 白名单内；两文件等长以便调包用例直击摘要层。 */
    private JSecureUploadRequest standardPushRequest() {
        return upload()
                .transNo(UUID.randomUUID().toString())
                .transType("T030422")
                .add("invoice-bytes".getBytes(StandardCharsets.UTF_8), "file-1", "invoice", "发票.pdf")
                .add("id_card-bytes".getBytes(StandardCharsets.UTF_8), "file-2", "id_card", "身份证背面.jpg")
                .prepare().getRequest();
    }

    /** meta part + 协议头骨架（文件 part 由负路径用例按需定制附加）。 */
    private MockMultipartHttpServletRequestBuilder multipartSkeleton(JSecureUploadRequest request) {
        org.springframework.mock.web.MockPart metaPart = new org.springframework.mock.web.MockPart(
                "meta", null, request.getMetaJson().getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON);
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart("/secure/upload").part(metaPart);
        request.getHeaders().forEach(builder::header);
        return builder;
    }

    /** 全量正向组装并断言协议码字段存在。 */
    private org.springframework.test.web.servlet.ResultActions perform(JSecureUploadRequest request) throws Exception {
        MockMultipartHttpServletRequestBuilder builder = multipartSkeleton(request);
        request.getFiles().forEach(file -> builder.file(new org.springframework.mock.web.MockMultipartFile(
                "files", file.getFileId(), file.getContentType(), file.getContent())));
        return mockMvc.perform(builder)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").exists());
    }
}
