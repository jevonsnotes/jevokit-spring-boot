package io.github.jevonsnotes.jevokit.api;

import com.alibaba.fastjson2.JSON;
import jakarta.validation.Valid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.hamcrest.Matchers;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import io.github.jevonsnotes.jevokit.api.annotation.JSecureApi;
import io.github.jevonsnotes.jevokit.api.client.JSecureClient;
import io.github.jevonsnotes.jevokit.api.client.UploadChannel;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiConfiguration;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiProperties;
import io.github.jevonsnotes.jevokit.api.dto.FetchMode;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;
import io.github.jevonsnotes.jevokit.api.upload.JSecureUploadFileRefChecker;
import io.github.jevonsnotes.jevokit.api.upload.JSecureUploadUtil;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JSecure 上传服务端边界测试（@WebMvcTest，独立于 SecureUploadApiTest 的防线用例）：
 * 0 字节文件、push 侧 size 谎报（声明值≠实际字节数的 verifyUpload 全管线路径）、
 * fileName 路径穿越字符（仅展示语义）与无扩展名、meta part 缺失/重复/Content-Type 错误、
 * files 空清单/空字段/纯 refer 无文件 part、超长 fileId 与 50 条目基本可用性。
 *
 * <p>断言原则：拒绝路径必须给出可定位消息（code=999 + message 关键词）；
 * 歧义报文（如重复 meta part）按安全组件应然断言"不得静默受理"。
 */
@WebMvcTest
@Import({RefreshAutoConfiguration.class, JevokitApiConfiguration.class,
        SecureUploadEdgeTest.EdgeReferHookConfig.class, SecureUploadEdgeTest.EdgeUploadController.class})
@TestPropertySource(properties = {
        "jevokit.api.client[0].id=upload-edge-client",
        "jevokit.api.client[0].secret-key=0123456789abcdef",
        // 演示 SM2 密钥对（与 sample application.yml 同源，仅用于测试）
        "jevokit.api.client[0].private-key=MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk5B6bcSXNwvtfd6nufsDHEPOu8N2hHKJMBW5blZP2eSgCgYIKoEcz1UBgi2hRANCAASxO58/zvEseRlMZtRlojzn7m4jAaZPTDSOalPCSk3nnGW0GmEu2ZhfWDNH+Nq8wA6BLodb2gwI9NF+F1Mp3p9R",
        "jevokit.api.client[0].public-key=MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEsTufP87xLHkZTGbUZaI85+5uIwGmT0w0jmpTwkpN55xltBphLtmYX1gzR/javMAOgS6HW9oMCPTRfhdTKd6fUQ==",
        "jevokit.api.client[0].verify=true",
        "jevokit.api.client[0].prevent-replay=false",
        "jevokit.api.client[0].decrypt=true",
        "jevokit.api.client[0].encrypt=true",
})
@DisplayName("JSecure 上传服务端边界")
class SecureUploadEdgeTest {

    private static final String URL = "/secure/upload";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JevokitApiProperties properties;

    private JevokitApiClientProperties client() {
        return properties.getClient().get(0);
    }

    // ------------------------------------------------------------------
    // 0 字节文件（digest 为空串的 sha256；元数据是否接受）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("0 字节文件必受理：空内容 digest 为空串 sha256，size=0，全管线 code=0")
    void zeroBytePushFileAccepted() throws Exception {
        byte[] content = new byte[0];
        JSecureUploadRequest request = buildRequest(builder -> builder
                .add(content, "empty-1", "invoice", "空文件.pdf"));

        // 客户端组装侧：size=0 且 digest 为空串的 sha256（不是空值）
        JSecureUploadFileDto entry = decryptEntries(request).get(0);
        assertThat(entry.getSize()).isZero();
        assertThat(entry.getDigest())
                .as("0 字节文件摘要应为 sha256(空) 而非空串")
                .isEqualTo(JSecureUploadUtil.digestHex(content, "sha256"));

        perform(request)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
    }

    // ------------------------------------------------------------------
    // push 侧 size 谎报（fetcher 侧已测，此处为 verifyUpload 全管线路径）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("push size 少报必拒：声明 100 实际 200 字节，code=999「大小与声明不符」")
    void pushSizeUnderstatedRejected() throws Exception {
        byte[] content = "x".repeat(200).getBytes(StandardCharsets.UTF_8);
        JSecureUploadFileDto lying = pushEntry("file-lie", "invoice", "发票.pdf", "pdf", 100L,
                JSecureUploadUtil.digestHex(content, "sha256"), "sha256");
        JSecureUploadRequest request = buildRequest(builder -> builder.entry(lying, content));
        perform(request)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message", Matchers.containsString("大小与声明不符")));
    }

    @Test
    @DisplayName("push size 多报必拒：声明 999 实际 9 字节，code=999「大小与声明不符」")
    void pushSizeOverstatedRejected() throws Exception {
        byte[] content = "123456789".getBytes(StandardCharsets.UTF_8);
        JSecureUploadFileDto lying = pushEntry("file-lie2", "invoice", "发票.pdf", "pdf", 999L,
                JSecureUploadUtil.digestHex(content, "sha256"), "sha256");
        JSecureUploadRequest request = buildRequest(builder -> builder.entry(lying, content));
        perform(request)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message", Matchers.containsString("大小与声明不符")));
    }

    // ------------------------------------------------------------------
    // fileName 边界（展示字段语义 / 无扩展名）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("fileName 含 ../ 必受理：展示字段不参与任何落盘路径，format 按扩展名推断不受影响，code=0")
    void traversalFileNameAcceptedAsDisplayOnly() throws Exception {
        String evilName = "../../../etc/passwd.pdf";
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);
        JSecureUploadRequest request = buildRequest(builder -> builder
                .add(content, "file-1", "invoice", evilName));

        JSecureUploadFileDto entry = decryptEntries(request).get(0);
        assertThat(entry.getFileName()).as("fileName 应原样保留（仅展示语义）").isEqualTo(evilName);
        assertThat(entry.getFormat()).isEqualTo("pdf");

        perform(request)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("无扩展名 fileName：终端未配置 format 白名单时放行，code=0（format 留空不参与校验/落盘）")
    void extensionLessFileNameAcceptedWithoutWhitelist() throws Exception {
        // 随商榷点 2（无扩展名文件无法 push）修复：未配置 format 白名单的终端无校验需求、放行，
        // 原"缺少 format code=999"断言随行为放宽更新；配置了白名单的终端仍必拒（format 空不可能命中列表，
        // 见 SecureUploadApiTest 的 format 白名单防线用例）
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);
        JSecureUploadRequest request = buildRequest(builder -> builder
                .add(content, "file-noext", "invoice", "README"));
        perform(request)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
    }

    // ------------------------------------------------------------------
    // meta part 形态边界
    // ------------------------------------------------------------------

    @Test
    @DisplayName("meta part 缺失：files-only 请求应得 4xx（协议端点不得 5xx 崩溃）")
    void missingMetaPartRejectedWithClientError() throws Exception {
        JSecureUploadRequest request = buildRequest(builder -> builder
                .add("bytes".getBytes(StandardCharsets.UTF_8), "file-1", "invoice", "发票.pdf"));
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(URL);
        request.getHeaders().forEach(builder::header);
        request.getFiles().forEach(file -> builder.file(
                new org.springframework.mock.web.MockMultipartFile("files", file.getFileId(),
                        file.getContentType(), file.getContent())));
        mockMvc.perform(builder)
                .andExpect(MockMvcResultMatchers.status().is4xxClientError());
    }

    @Test
    @DisplayName("重复 meta part 必拒：同名两个 meta 的歧义报文不得静默受理（安全组件应然）")
    void duplicateMetaPartMustNotBeSilentlyAccepted() throws Exception {
        JSecureUploadRequest request = buildRequest(builder -> builder
                .add("bytes".getBytes(StandardCharsets.UTF_8), "file-1", "invoice", "发票.pdf"));
        MockPart meta1 = metaPart(request);
        MockPart meta2 = metaPart(request);
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(URL)
                .part(meta1).part(meta2);
        request.getHeaders().forEach(builder::header);
        request.getFiles().forEach(file -> builder.file(
                new org.springframework.mock.web.MockMultipartFile("files", file.getFileId(),
                        file.getContentType(), file.getContent())));

        MvcResult result = mockMvc.perform(builder).andReturn();
        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(status).as("重复 meta part 的实际 HTTP 状态，200 即静默受理（记录于缺陷清单）").isLessThan(500);
        assertThat(body)
                .as("同名重复 meta part 属歧义报文，不得以 code=0 静默受理")
                .doesNotContain("\"code\":\"0\"");
    }

    @Test
    @DisplayName("meta part Content-Type=text/plain：应得 4xx（不进 JSON 转换器即不进协议链路，记录形态）")
    void metaPartWithTextPlainContentTypeRejectedWithClientError() throws Exception {
        JSecureUploadRequest request = buildRequest(builder -> builder
                .add("bytes".getBytes(StandardCharsets.UTF_8), "file-1", "invoice", "发票.pdf"));
        MockPart metaPlainText = new MockPart("meta", null,
                request.getMetaJson().getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN);
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(URL).part(metaPlainText);
        request.getHeaders().forEach(builder::header);
        request.getFiles().forEach(file -> builder.file(
                new org.springframework.mock.web.MockMultipartFile("files", file.getFileId(),
                        file.getContentType(), file.getContent())));
        mockMvc.perform(builder)
                .andExpect(MockMvcResultMatchers.status().is4xxClientError());
    }

    // ------------------------------------------------------------------
    // files 清单边界：空列表 / null 字段 / 纯 refer 无文件 part
    // ------------------------------------------------------------------

    @Test
    @DisplayName("files 空列表 []：无文件 part 的纯 meta multipart 必受理，code=0（0/0）")
    void emptyFilesListAccepted() throws Exception {
        JSecureUploadRequest request = buildRequest(builder -> {
            // 不追加任何文件条目 → files=[]
        });
        assertAcceptedCounts(perform(request), 0, 0);
    }

    @Test
    @DisplayName("files 字段为 null（手工信封）：与空列表同语义，必受理 code=0")
    void nullFilesFieldAccepted() throws Exception {
        Map<String, Object> biz = Map.of("orderNo", "SO-NULL-FILES");
        JSecureUploadDto<Map<String, Object>, JSecureUploadFileDto> upload =
                new JSecureUploadDto<>(biz, null);
        JSecureRequest<Object> envelope = new JSecureRequest<>();
        envelope.setTransNo("t-null-files");
        JSecureApiUtil.sealRequest(envelope, JSON.toJSONString(upload), client());

        MockPart metaPart = new MockPart("meta", null,
                JSON.toJSONString(envelope).getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON);
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(URL).part(metaPart);
        JSecureApiUtil.buildProtocolHeaders(client()).forEach(builder::header);
        assertAcceptedCounts(mockMvc.perform(builder)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0")), 0, 0);
    }

    @Test
    @DisplayName("纯 refer 无文件 part：钩子判定存在即放行，code=0（1/0）")
    void pureReferEntriesWithoutFilePartAccepted() throws Exception {
        JSecureUploadRequest request = buildRequest(builder -> builder.refer("archived-1"));
        assertAcceptedCounts(perform(request), 1, 0);
    }

    // ------------------------------------------------------------------
    // 规模边界：超长 fileId / 50 条目
    // ------------------------------------------------------------------

    @Test
    @DisplayName("超长 fileId（1000 字符）：随 BUG-4/5 白名单收紧改为拒绝（长度上限 128）")
    void thousandCharFileIdRejected() {
        // 随 BUG-4/5 fileId 字符集白名单收紧（长度 1-128）：超长 fileId 从"受理"变"拒绝"，
        // 原"对齐与摘要校验仍正确 code=0"断言随行为收紧更新
        String longFileId = "f".repeat(1000);
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> JSecureClient.of(client().getId())
                .upload(URL, Map.<String, Object>of("orderNo", "SO-EDGE-001"), Void.class)
                .transNo(UUID.randomUUID().toString())
                .add(content, longFileId, "invoice", "发票.pdf"))
                .as("超长 fileId 应在客户端入口 fail-fast 拒绝")
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("fileId");
    }

    @Test
    @DisplayName("50 个文件条目：全量对齐可用（code=0，50/50），无数量上限误伤")
    void fiftyPushEntriesAccepted() throws Exception {
        JSecureUploadRequest request = buildRequest(builder -> {
            for (int i = 1; i <= 50; i++) {
                builder.add(("content-" + i).getBytes(StandardCharsets.UTF_8), "file-" + i, "invoice", "发票-" + i + ".pdf");
            }
        });
        assertAcceptedCounts(perform(request), 50, 50);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 以标准业务体构建请求（customizer 内组装文件条目）。 */
    private JSecureUploadRequest buildRequest(Consumer<UploadChannel<Map<String, Object>, Void>> customizer) {
        UploadChannel<Map<String, Object>, Void> channel = JSecureClient.of(client().getId())
                .upload(URL, Map.<String, Object>of("orderNo", "SO-EDGE-001"), Void.class)
                .transNo(UUID.randomUUID().toString());
        customizer.accept(channel);
        return channel.prepare().getRequest();
    }

    /** 全量正向组装并发起请求。 */
    private org.springframework.test.web.servlet.ResultActions perform(JSecureUploadRequest request) throws Exception {
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(URL).part(metaPart(request));
        request.getHeaders().forEach(builder::header);
        request.getFiles().forEach(file -> builder.file(
                new org.springframework.mock.web.MockMultipartFile("files", file.getFileId(),
                        file.getContentType(), file.getContent())));
        return mockMvc.perform(builder)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").exists());
    }

    /** 断言受理回执的 declared/bound 计数（响应 data 为密文，按终端密钥解密后核对）。 */
    private void assertAcceptedCounts(org.springframework.test.web.servlet.ResultActions actions,
                                      int declared, int bound) throws Exception {
        actions.andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
        MvcResult result = actions.andReturn();
        com.alibaba.fastjson2.JSONObject envelope =
                com.alibaba.fastjson2.JSONObject.parseObject(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String view = io.github.jevonsnotes.jevokit.core.util.SecurityUtil
                .sm4cbcDecrypt(envelope.getString("data"), client().getSecretKeySm4());
        assertThat(view).as("回执应回显 declared/bound 计数").contains("accepted:" + declared + "/" + bound);
    }

    private MockPart metaPart(JSecureUploadRequest request) {
        return new MockPart("meta", null,
                request.getMetaJson().getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON);
    }

    /** 服务端同源配方解出构建产物里的文件清单（断言客户端组装形态用）。 */
    private List<JSecureUploadFileDto> decryptEntries(JSecureUploadRequest request) {
        com.alibaba.fastjson2.JSONObject envelope = com.alibaba.fastjson2.JSONObject.parseObject(request.getMetaJson());
        String view = io.github.jevonsnotes.jevokit.core.util.SecurityUtil
                .sm4cbcDecrypt(envelope.getString("data"), client().getSecretKeySm4());
        return com.alibaba.fastjson2.JSONObject.parseObject(view, JSecureUploadDto.class).getFiles();
    }

    /** 手工构造 push 条目（size 谎报等"作弊"场景）。 */
    private JSecureUploadFileDto pushEntry(String fileId, String fileType, String fileName, String format,
                                           Long size, String digest, String digestAlg) {
        JSecureUploadFileDto entry = new JSecureUploadFileDto();
        entry.setFileId(fileId);
        entry.setFileType(fileType);
        entry.setFileName(fileName);
        entry.setFormat(format);
        entry.setSize(size);
        entry.setDigest(digest);
        entry.setDigestAlg(digestAlg);
        entry.setFetchMode(FetchMode.push);
        return entry;
    }

    /** refer 钩子测试配置（存在集 archived-1）。 */
    @TestConfiguration
    static class EdgeReferHookConfig {
        @Bean
        JSecureUploadFileRefChecker edgeRefChecker() {
            return file -> Set.of("archived-1").contains(file.getFileId());
        }
    }

    /** 被测端点：meta + files 双参数形态，message 回显 declared/bound 便于断言。 */
    @RestController
    static class EdgeUploadController {

        @JSecureApi
        @PostMapping("/secure/upload")
        public JSecureResponse<String> upload(
                @RequestPart("meta") @Valid JSecureRequest<JSecureUploadDto<Map<String, Object>, JSecureUploadFileDto>> meta,
                @RequestPart(value = "files", required = false) List<MultipartFile> files) {
            int declared = meta.getTarget().getFiles() == null ? 0 : meta.getTarget().getFiles().size();
            int bound = files == null ? 0 : files.size();
            return JSecureResponse.ok("accepted:" + declared + "/" + bound);
        }
    }
}
