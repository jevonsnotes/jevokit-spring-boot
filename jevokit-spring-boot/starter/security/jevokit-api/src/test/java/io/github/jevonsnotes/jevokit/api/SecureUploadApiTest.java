package io.github.jevonsnotes.jevokit.api;

import jakarta.validation.Valid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.hamcrest.Matchers;
import org.springframework.web.multipart.MultipartFile;
import io.github.jevonsnotes.jevokit.api.annotation.JSecureApi;
import io.github.jevonsnotes.jevokit.api.client.JSecureClient;
import io.github.jevonsnotes.jevokit.api.client.UploadChannel;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiConfiguration;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiProperties;
import io.github.jevonsnotes.jevokit.api.dto.FetchMode;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDetailDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * JSecure 上传管线防线切片测试（@WebMvcTest，meta+files 双参数端点 + @RequestBody 装配错误端点）。
 *
 * <p>覆盖防线用例：调包必拒（两个文件 filename 互换）、注入/丢文件必拒（数量双向）、digest 篡改必拒、
 * digestAlg=sm3 正向、非 multipart + 上传 DTO 必拒（装配错误 fail-fast）、format 白名单 fail-closed、
 * size 上限、pull 白名单外必拒、未配白名单有 pull 必拒、refer 无钩子必拒（正向路径见
 * {@code SecureUploadReferHookTest}）。正向对照：合规 push 请求 code=0。
 *
 * <p>客户端报文由 {@link UploadChannel}（JSecureClient 上传通道）真实构造（协议头签名 + SM4 加密信封 + SM2 体签名），
 * 负路径仅在传输前定点篡改 multipart 组装（换 filename / 增删文件 part / 改摘要），失败原因可归因。
 */
@WebMvcTest
@Import({RefreshAutoConfiguration.class, JevokitApiConfiguration.class, SecureUploadApiTest.UploadController.class})
@TestPropertySource(properties = {
        "jevokit.api.client[0].id=upload-test-client",
        "jevokit.api.client[0].secret-key=0123456789abcdef",
        // 演示 SM2 密钥对（与 sample application.yml 同源，仅用于测试）
        "jevokit.api.client[0].private-key=MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk5B6bcSXNwvtfd6nufsDHEPOu8N2hHKJMBW5blZP2eSgCgYIKoEcz1UBgi2hRANCAASxO58/zvEseRlMZtRlojzn7m4jAaZPTDSOalPCSk3nnGW0GmEu2ZhfWDNH+Nq8wA6BLodb2gwI9NF+F1Mp3p9R",
        "jevokit.api.client[0].public-key=MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEsTufP87xLHkZTGbUZaI85+5uIwGmT0w0jmpTwkpN55xltBphLtmYX1gzR/javMAOgS6HW9oMCPTRfhdTKd6fUQ==",
        "jevokit.api.client[0].verify=true",
        "jevokit.api.client[0].prevent-replay=true",
        "jevokit.api.client[0].decrypt=true",
        "jevokit.api.client[0].encrypt=true",
})
@DisplayName("JSecure 上传防线切片")
class SecureUploadApiTest {

    private static final String URL = "/secure/upload";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JevokitApiProperties properties;

    /** 便捷取本测试终端配置（防线参数运行时按用例调节）。 */
    private JevokitApiClientProperties client() {
        return properties.getClient().get(0);
    }

    // ------------------------------------------------------------------
    // 正向对照：合规 push 请求走通全管线
    // ------------------------------------------------------------------

    @Test
    @DisplayName("正向对照：push 双文件（发票+身份证）全管线通过，code=0")
    void validPushRequestAccepted() throws Exception {
        JSecureUploadRequest request = standardPushRequest();
        perform(request).andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
    }

    // ------------------------------------------------------------------
    // 防线 1：调包必拒（两个文件 filename 互换——发票内容顶着身份证的 fileId）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("调包必拒：两个文件 part 的 filename 互换后摘要对不上，code=999")
    void swappedFilePartsRejected() throws Exception {
        JSecureUploadRequest request = standardPushRequest();
        // 组装时互换 filename：file-1 的 part 携带 file-2 的内容（对齐键错位 → 摘要校验失败）
        MockMultipartHttpServletRequestBuilder builder = multipartSkeleton(request);
        builder.file(filePart("file-2-content".getBytes(StandardCharsets.UTF_8), request.getFiles().get(0).getFileId()));
        builder.file(filePart("file-1-content".getBytes(StandardCharsets.UTF_8), request.getFiles().get(1).getFileId()));
        perform(builder)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message", Matchers.containsString("摘要校验失败")));
    }

    // ------------------------------------------------------------------
    // 防线 2：注入/丢文件必拒（push 条目数 = 文件 part 数，双向）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("注入必拒：多带一个未申报的文件 part，code=999 数量不一致")
    void extraFilePartRejected() throws Exception {
        JSecureUploadRequest request = standardPushRequest();
        MockMultipartHttpServletRequestBuilder builder = multipartSkeleton(request);
        request.getFiles().forEach(file -> builder.file(filePart(file.getContent(), file.getFileId())));
        builder.file(filePart("injected".getBytes(StandardCharsets.UTF_8), "file-extra"));
        perform(builder)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message", Matchers.containsString("不一致")));
    }

    @Test
    @DisplayName("丢文件必拒：申报两个文件只发一个 part，code=999 数量不一致")
    void missingFilePartRejected() throws Exception {
        JSecureUploadRequest request = standardPushRequest();
        MockMultipartHttpServletRequestBuilder builder = multipartSkeleton(request);
        builder.file(filePart(request.getFiles().get(0).getContent(), request.getFiles().get(0).getFileId()));
        perform(builder)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message", Matchers.containsString("不一致")));
    }

    // ------------------------------------------------------------------
    // 防线 3：digest 篡改必拒；digestAlg=sm3 正向
    // ------------------------------------------------------------------

    @Test
    @DisplayName("digest 篡改必拒：申报摘要与文件实际内容不符，code=999")
    void tamperedDigestRejected() throws Exception {
        byte[] content = "file-1-content".getBytes(StandardCharsets.UTF_8);
        JSecureUploadFileDetailDto lying = pushEntry("file-1", "invoice", "invoice.pdf", "pdf", (long) content.length,
                "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef0", "sha256");
        JSecureUploadRequest request = buildRequest(builder -> builder.entry(lying, content));
        perform(request)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message", Matchers.containsString("摘要校验失败")));
    }

    @Test
    @DisplayName("digestAlg=sm3 正向：国密摘要路径全管线通过，code=0")
    void sm3DigestAccepted() throws Exception {
        byte[] content = "sm3-content".getBytes(StandardCharsets.UTF_8);
        JSecureUploadRequest request = buildRequest(builder -> builder.entry(
                pushEntry("file-sm3", "invoice", "发票.pdf", "pdf", (long) content.length,
                        io.github.jevonsnotes.jevokit.api.upload.JSecureUploadUtil.digestHex(content, "sm3"), "sm3"),
                content));
        perform(request).andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
    }

    // ------------------------------------------------------------------
    // 加密条目（add(..., true)：SM4-CBC 内部加密，digest 按明文，服务端无感解密）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("加密正向：明文上传内部加密，part 为密文、digest 为明文摘要；姿势一端点无感解密回读一致")
    void encryptedPushRoundTrip() throws Exception {
        byte[] plain = "机密发票内容-加密往返-ENC-001".getBytes(StandardCharsets.UTF_8);
        JSecureUploadRequest request = buildRequest(builder -> builder
                .add(plain, "file-enc-1", "invoice", "发票.pdf", true));
        // 客户端产物断言：part 字节是密文（不等于明文），条目 digest 是明文摘要、size 是密文长度
        byte[] wireBytes = request.getFiles().get(0).getContent();
        org.assertj.core.api.Assertions.assertThat(wireBytes).isNotEqualTo(plain);
        org.assertj.core.api.Assertions.assertThat(request.getFiles().get(0).getContent().length)
                .isGreaterThan(plain.length);   // IV 16B + padding
        // 服务端：姿势一端点（单 meta 参数）无感解密回读明文——code=0 即证明解密+明文摘要复核全过
        MockMultipartHttpServletRequestBuilder plainBuilder = multipartSkeleton("/secure/upload-plain", request);
            request.getFiles().forEach(file -> plainBuilder.file(filePart(file.getContent(), file.getFileId())));
            mockMvc.perform(plainBuilder)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("密文篡改必拒：加密条目 part 字节改一位，解密后摘要必失配，code=999")
    void tamperedCipherRejected() throws Exception {
        byte[] plain = "机密发票内容-加密篡改-ENC-002".getBytes(StandardCharsets.UTF_8);
        JSecureUploadRequest request = buildRequest(builder -> builder
                .add(plain, "file-enc-2", "invoice", "发票.pdf", true));
        byte[] cipher = request.getFiles().get(0).getContent();
        cipher[cipher.length - 1] ^= 0x01;   // 翻转最后一个密文字节
        MockMultipartHttpServletRequestBuilder builder = multipartSkeleton(request);
        builder.file(filePart(cipher, "file-enc-2"));
        mockMvc.perform(builder)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"));
    }

    @Test
    @DisplayName("digest 开关关闭：加密条目仍无感解密（业务依赖 plainContent），摘要复核豁免")
    void encryptedStillDecryptedWhenDigestVerifyOff() throws Exception {
        boolean original = client().isUploadDigestVerify();
        try {
            client().setUploadDigestVerify(false);
            byte[] plain = "开关关也解密-ENC-003".getBytes(StandardCharsets.UTF_8);
            JSecureUploadRequest request = buildRequest(builder -> builder
                    .add(plain, "file-enc-3", "invoice", "发票.pdf", true));
            MockMultipartHttpServletRequestBuilder plainBuilder = multipartSkeleton("/secure/upload-plain", request);
            request.getFiles().forEach(file -> plainBuilder.file(filePart(file.getContent(), file.getFileId())));
            mockMvc.perform(plainBuilder)
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
        } finally {
            client().setUploadDigestVerify(original);
        }
    }

    @Test
    @DisplayName("加密条目仅限 push：pull/refer 条目标志 encrypted 必拒，code=999")
    void encryptedPullRejected() throws Exception {
        JSecureUploadFileDetailDto entry = new JSecureUploadFileDetailDto();
        entry.setFileId("file-enc-pull");
        entry.setDigest("deadbeef");
        entry.setDigestAlg("sha256");
        entry.setEncrypted(true);
        entry.setFetchMode(FetchMode.pull);
        entry.setUrl("http://127.0.0.1:18080/whatever.bin");
        JSecureUploadRequest request = buildRequest(builder -> builder.entry(entry, null));
        perform(request)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                        Matchers.containsString("仅支持 push")));
    }

    @Test
    @DisplayName("条目数超上限必拒：upload-max-file-count=1 时两文件条目，code=999（0 或负数不限制）")
    void fileCountOverLimitRejected() throws Exception {
        int original = client().getUploadMaxFileCount();
        try {
            client().setUploadMaxFileCount(1);
            JSecureUploadRequest request = standardPushRequest();   // 双文件（file-1 + file-2）
            perform(request)
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                            Matchers.containsString("条目数超上限")));
            client().setUploadMaxFileCount(0);                      // 0 = 不限制
            perform(standardPushRequest())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
        } finally {
            client().setUploadMaxFileCount(original);
        }
    }

    @Test
    @DisplayName("digestAlg=md5 正向：MD5 摘要路径全管线通过，code=0（协议全集 sha256/sm3/md5）")
    void md5DigestAccepted() throws Exception {
        byte[] content = "md5-content".getBytes(StandardCharsets.UTF_8);
        JSecureUploadRequest request = buildRequest(builder -> builder.entry(
                pushEntry("file-md5", "invoice", "发票.pdf", "pdf", (long) content.length,
                        "86e3504191d98216bcad8eaf14093d98", "md5"), content));
        perform(request).andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("digestAlg 范围外必拒：终端限定 [sm3] 后 sha256 条目被拒，code=999 提示带允许清单")
    void digestAlgOutOfTerminalScopeRejected() throws Exception {
        java.util.List<String> original = client().getUploadDigestAlgorithms();
        try {
            client().setUploadDigestAlgorithms(java.util.List.of("sm3"));
            byte[] content = "sm3-only".getBytes(StandardCharsets.UTF_8);
            JSecureUploadRequest request = buildRequest(builder -> builder.entry(
                    pushEntry("file-scope", "invoice", "发票.pdf", "pdf", (long) content.length,
                            io.github.jevonsnotes.jevokit.api.upload.JSecureUploadUtil.digestHex(content, "sha256"), "sha256"),
                    content));
            perform(request)
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                            Matchers.allOf(Matchers.containsString("摘要算法不在允许范围"),
                                    Matchers.containsString("sm3"))));
        } finally {
            client().setUploadDigestAlgorithms(original);
        }
    }

    @Test
    @DisplayName("摘要校验开关关闭：upload-digest-verify=false 时错摘要/缺摘要均受理")
    void digestVerifyDisabledSkipsDigestCheck() throws Exception {
        boolean original = client().isUploadDigestVerify();
        try {
            client().setUploadDigestVerify(false);
            byte[] content = "no-digest-check".getBytes(StandardCharsets.UTF_8);
            // 错误摘要 + 缺失摘要两种形态都应受理（开关关闭即跳过比对与必填；size 校验独立开关不受影响）
            JSecureUploadRequest lying = buildRequest(builder -> builder.entry(
                    pushEntry("file-lying", "invoice", "发票.pdf", "pdf", (long) content.length,
                            "deadbeef", "sha256"), content));
            perform(lying).andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
            JSecureUploadRequest noDigest = buildRequest(builder -> builder.entry(
                    pushEntry("file-none", "invoice", "发票.pdf", "pdf", (long) content.length, null, null),
                    content));
            perform(noDigest).andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
        } finally {
            client().setUploadDigestVerify(original);
        }
    }

    @Test
    @DisplayName("大小校验开关关闭：缺 size/声明不符均受理；upload-max-size 上限恒拒（不受开关豁免）")
    void sizeVerifyDisabledSkipsSizeCheck() throws Exception {
        boolean original = client().isUploadSizeVerify();
        try {
            client().setUploadSizeVerify(false);
            byte[] content = "no-size-check".getBytes(StandardCharsets.UTF_8);
            // 缺 size（push 必填豁免；digest 仍按独立开关正确声明，防两开关互扰）+ 声明不符（比对豁免）受理；
            String goodDigest = io.github.jevonsnotes.jevokit.api.upload.JSecureUploadUtil.digestHex(content, "sha256");
            JSecureUploadRequest noSize = buildRequest(builder -> builder.entry(
                    pushEntry("file-nosize", "invoice", "发票.pdf", "pdf", null, goodDigest, "sha256"), content));
            perform(noSize).andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
            JSecureUploadRequest wrongSize = buildRequest(builder -> builder.entry(
                    pushEntry("file-wrongsize", "invoice", "发票.pdf", "pdf", (long) content.length + 1,
                            goodDigest, "sha256"), content));
            perform(wrongSize).andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
            JSecureUploadRequest overLimit = buildRequest(builder -> builder.entry(
                    pushEntry("file-overlimit", "invoice", "发票.pdf", "pdf", Long.MAX_VALUE,
                            goodDigest, "sha256"), content));
            // upload-max-size 上限不受开关豁免：声明超限仍拒（终端资源保护，配多少就是多少）
            perform(overLimit)
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                            Matchers.containsString("超出大小上限")));
            // 摘要校验独立开关：size 关闭但摘要开关开启，错摘要仍拒
            JSecureUploadRequest lyingDigest = buildRequest(builder -> builder.entry(
                    pushEntry("file-lying2", "invoice", "发票.pdf", "pdf", null, "deadbeef", "sha256"), content));
            perform(lyingDigest)
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                            Matchers.containsString("摘要校验失败")));
        } finally {
            client().setUploadSizeVerify(original);
        }
    }

    // ------------------------------------------------------------------
    // 防线 4：非 multipart + 上传 DTO 必拒（@RequestBody 装配错误 fail-fast）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("非 multipart 必拒：上传 DTO 走 @RequestBody 装配（JSON 请求），code=999 提示 multipart")
    void nonMultipartUploadDtoRejected() throws Exception {
        JSecureUploadRequest request = standardPushRequest();
        // 文件 part 不随 JSON 请求发送——该端点本身即为错误的装配形态，服务端须 fail-fast
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post("/secure/upload-body")
                .content(request.getMetaJson())
                .contentType(MediaType.APPLICATION_JSON);
        request.getHeaders().forEach(builder::header);
        mockMvc.perform(builder)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message", Matchers.containsString("multipart")));
    }

    // ------------------------------------------------------------------
    // 防线 5：format 白名单 fail-closed + size 上限（终端级配置）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("format 白名单外必拒：invoice 仅允许 pdf，申报 jpg，code=999")
    void formatOutOfWhitelistRejected() throws Exception {
        try {
            client().getUploadFormatWhitelist().put("invoice", List.of("pdf"));
            byte[] content = "invoice-as-jpg".getBytes(StandardCharsets.UTF_8);
            JSecureUploadRequest request = buildRequest(builder -> builder.entry(
                    pushEntry("file-1", "invoice", "发票.jpg", "jpg", (long) content.length,
                            io.github.jevonsnotes.jevokit.api.upload.JSecureUploadUtil.digestHex(content, "sha256"), "sha256"),
                    content));
            perform(request)
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.message", Matchers.containsString("格式不在白名单")));
        } finally {
            client().getUploadFormatWhitelist().clear();
        }
    }

    @Test
    @DisplayName("fileType 未在白名单配置必拒（fail-closed）：申报未知类型 other，code=999")
    void fileTypeNotConfiguredRejected() throws Exception {
        try {
            client().getUploadFormatWhitelist().put("invoice", List.of("pdf"));
            byte[] content = "x".getBytes(StandardCharsets.UTF_8);
            JSecureUploadRequest request = buildRequest(builder -> builder.entry(
                    pushEntry("file-x", "other", "x.txt", "txt", (long) content.length,
                            io.github.jevonsnotes.jevokit.api.upload.JSecureUploadUtil.digestHex(content, "sha256"), "sha256"), content));
            perform(request)
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.message", Matchers.containsString("未在上传白名单")));
        } finally {
            client().getUploadFormatWhitelist().clear();
        }
    }

    @Test
    @DisplayName("size 超限必拒：终端上限 10 字节，申报 13 字节文件，code=999")
    void sizeOverLimitRejected() throws Exception {
        try {
            client().setUploadMaxSize(10);
            JSecureUploadRequest request = standardPushRequest();
            perform(request)
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.message", Matchers.containsString("大小上限")));
        } finally {
            client().setUploadMaxSize(JevokitApiClientProperties.DEFAULT_UPLOAD_MAX_SIZE);
        }
    }

    // ------------------------------------------------------------------
    // 防线 6：pull 白名单（未配置必拒 / 域名外必拒 / 域名内放行）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("pull 未配白名单必拒：终端无 upload-fetch-url-whitelist 而出现 pull 条目，code=999 fail-fast")
    void pullWithoutWhitelistRejected() throws Exception {
        JSecureUploadRequest request = buildRequest(builder -> builder
                .pull("remote-1", "https://files.partner.com/a.pdf", 3L, null, "invoice", "pdf"));
        perform(request)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message", Matchers.containsString("不在白名单")));
    }

    @Test
    @DisplayName("pull 域名外必拒：白名单 partner.com，申报 evil.com，code=999")
    void pullOutOfWhitelistRejected() throws Exception {
        try {
            client().getUploadFetchUrlWhitelist().add("*.partner.com");
            JSecureUploadRequest request = buildRequest(builder -> builder
                    .pull("remote-1", "https://evil.com/a.pdf", 3L, null, "invoice", "pdf"));
            perform(request)
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.message", Matchers.containsString("不在白名单")));
        } finally {
            client().getUploadFetchUrlWhitelist().clear();
        }
    }

    @Test
    @DisplayName("pull 域名内放行：通配 *.partner.com 命中子域，元数据层通过 code=0（下载时机属业务决策）")
    void pullWithinWhitelistAccepted() throws Exception {
        try {
            client().getUploadFetchUrlWhitelist().add("*.partner.com");
            JSecureUploadRequest request = buildRequest(builder -> builder
                    .pull("remote-1", "https://cdn.partner.com/invoice/a.pdf?sign=abc", 3L, null, "invoice", "pdf"));
            perform(request).andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
        } finally {
            client().getUploadFetchUrlWhitelist().clear();
        }
    }

    // ------------------------------------------------------------------
    // 防线 7：refer 无钩子必拒（正向路径见 SecureUploadReferHookTest）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("refer 无钩子必拒：容器未注册 JSecureUploadFileRefChecker，code=999")
    void referWithoutHookRejected() throws Exception {
        JSecureUploadRequest request = buildRequest(builder -> builder.refer("archived-1"));
        perform(request)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message", Matchers.containsString("JSecureUploadFileRefChecker")));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 标准正向请求：发票（file-1）+ 身份证（file-2）双 push 文件。 */
    private JSecureUploadRequest standardPushRequest() {
        return buildRequest(builder -> builder
                .add("file-1-content".getBytes(StandardCharsets.UTF_8), "file-1", "invoice", "invoice.pdf")
                .add("file-2-content".getBytes(StandardCharsets.UTF_8), "file-2", "id_card", "身份证背面.jpg"));
    }

    /** 以标准业务体构建请求（customizer 内组装文件条目）。 */
    private JSecureUploadRequest buildRequest(Consumer<UploadChannel<UploadOrderBiz, Void>> customizer) {
        UploadOrderBiz biz = new UploadOrderBiz();
        biz.setOrderNo("SO-2026-001");
        biz.setAmount(199.00);
        UploadChannel<UploadOrderBiz, Void> channel = JSecureClient.of(client().getId())
                .upload(URL, biz, Void.class)
                .transNo(UUID.randomUUID().toString());
        customizer.accept(channel);
        return channel.prepare().getRequest();
    }

    /** 全量正向组装：meta part + 协议头 + 全部文件 part。 */
    private MockMultipartHttpServletRequestBuilder multipartSkeleton(JSecureUploadRequest request) {
        return multipartSkeleton(URL, request);
    }

    /** 同上，可指定端点（如姿势一 /secure/upload-plain）。 */
    private MockMultipartHttpServletRequestBuilder multipartSkeleton(String url, JSecureUploadRequest request) {
        MockPart metaPart = new MockPart("meta", null,
                request.getMetaJson().getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON);
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(url).part(metaPart);
        request.getHeaders().forEach(builder::header);
        return builder;
    }

    /** 按 multipartSkeleton 附加全部文件 part 并发起请求。 */
    private org.springframework.test.web.servlet.ResultActions perform(JSecureUploadRequest request) throws Exception {
        MockMultipartHttpServletRequestBuilder builder = multipartSkeleton(request);
        request.getFiles().forEach(file -> builder.file(filePart(file.getContent(), file.getFileId())));
        return perform(builder);
    }

    private org.springframework.test.web.servlet.ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").exists());
    }

    private org.springframework.mock.web.MockMultipartFile filePart(byte[] content, String fileId) {
        return new org.springframework.mock.web.MockMultipartFile("files", fileId, "application/octet-stream", content);
    }

    /** 手工构造 push 条目（防线用例需要"谎报"字段——digest/算法/格式等）。 */
    private JSecureUploadFileDetailDto pushEntry(String fileId, String fileType, String fileName, String format,
                                           Long size, String digest, String digestAlg) {
        JSecureUploadFileDetailDto entry = new JSecureUploadFileDetailDto();
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

    /** 业务数据样例。 */
    public static class UploadOrderBiz {
        private String orderNo;
        private Double amount;

        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
    }

    /** 被测端点：meta + files 双参数形态（正确装配）。 */
    @RestController
    static class UploadController {

        @JSecureApi
        @PostMapping("/secure/upload")
        public JSecureResponse<String> upload(
                @RequestPart("meta") @Valid JSecureRequest<JSecureUploadDto<UploadOrderBiz, JSecureUploadFileDetailDto>> meta,
                @RequestPart(value = "files", required = false) List<MultipartFile> files) {
            int declared = meta.getTarget().getFileDetails() == null ? 0 : meta.getTarget().getFileDetails().size();
            int bound = files == null ? 0 : files.size();
            return JSecureResponse.ok("accepted:" + declared + "/" + bound);
        }

        /** 姿势一端点：单 meta 参数（无 files 绑定），加密条目经无感解密从 plainContent 回读明文。 */
        @JSecureApi
        @PostMapping("/secure/upload-plain")
        public JSecureResponse<String> uploadPlain(
                @RequestPart("meta") @Valid JSecureRequest<JSecureUploadDto<UploadOrderBiz, JSecureUploadFileDetailDto>> meta) {
            JSecureUploadFileDetailDto first = meta.getTarget().getFileDetails().get(0);
            return JSecureResponse.ok(first.getFileId() + ":" + (first.getPlainContent() == null
                    ? "null" : new String(first.getPlainContent(), StandardCharsets.UTF_8)));
        }

        /** 装配错误形态对照端点：上传 DTO 走 @RequestBody（非 multipart），服务端必须 fail-fast 拒绝。 */
        @JSecureApi
        @PostMapping("/secure/upload-body")
        public JSecureResponse<String> uploadBody(
                @RequestBody @Valid JSecureRequest<JSecureUploadDto<UploadOrderBiz, JSecureUploadFileDetailDto>> meta) {
            return JSecureResponse.ok("should-never-reach");
        }
    }
}
