package io.github.jevonsnotes.jevokit.api;

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
import org.hamcrest.Matchers;
import io.github.jevonsnotes.jevokit.api.client.JSecureClient;
import io.github.jevonsnotes.jevokit.api.client.UploadChannel;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiConfiguration;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiProperties;
import io.github.jevonsnotes.jevokit.api.dto.FetchMode;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDetailDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadRequest;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;
import io.github.jevonsnotes.jevokit.api.upload.JSecureUploadUtil;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 上传校验开关组合矩阵与算法范围交互的补充边界用例（测试工程师独立复核，
 * 补 {@code SecureUploadApiTest} 已有用例之间的组合缝隙；共享上下文内改终端配置一律 try/finally 恢复）。
 *
 * <p>覆盖：双关组合（内容层实际字节上限恒检 / 其余防线仍在）、digest 关+size 开的镜像独立性、
 * 空白 digestAlg 在受限范围下应拒、算法名与摘要值的大小写/空格、客户端 digestAlg() 链式
 * 对 add()/pull()/entry() 的作用口径、pull 声明 size 上限/负值恒检、refer 受算法范围牵连的现状刻画。
 */
@WebMvcTest
@Import({RefreshAutoConfiguration.class, JevokitApiConfiguration.class, SecureUploadApiTest.UploadController.class,
        SecureUploadSwitchBoundaryTest.ReferHookConfig.class})
@TestPropertySource(properties = {
        // 与 SecureUploadApiTest 不同 client id —— 隔离 Spring 上下文缓存，避免共享上下文状态互扰
        "jevokit.api.client[0].id=upload-switch-client",
        "jevokit.api.client[0].secret-key=0123456789abcdef",
        // 演示 SM2 密钥对（与 sample application.yml 同源，仅用于测试）
        "jevokit.api.client[0].private-key=MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk5B6bcSXNwvtfd6nufsDHEPOu8N2hHKJMBW5blZP2eSgCgYIKoEcz1UBgi2hRANCAASxO58/zvEseRlMZtRlojzn7m4jAaZPTDSOalPCSk3nnGW0GmEu2ZhfWDNH+Nq8wA6BLodb2gwI9NF+F1Mp3p9R",
        "jevokit.api.client[0].public-key=MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEsTufP87xLHkZTGbUZaI85+5uIwGmT0w0jmpTwkpN55xltBphLtmYX1gzR/javMAOgS6HW9oMCPTRfhdTKd6fUQ==",
        "jevokit.api.client[0].verify=true",
        "jevokit.api.client[0].prevent-replay=true",
        "jevokit.api.client[0].decrypt=true",
        "jevokit.api.client[0].encrypt=true",
})
@DisplayName("上传校验开关矩阵与算法范围边界")
class SecureUploadSwitchBoundaryTest {

    private static final String URL = "/secure/upload";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JevokitApiProperties properties;

    private JevokitApiClientProperties client() {
        return properties.getClient().get(0);
    }

    // ------------------------------------------------------------------
    // 开关组合矩阵：双关（digest 关 + size 关）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("双关组合：实际字节数超 upload-max-size 仍恒拒（内容层兜底不受任何开关豁免）")
    void bothSwitchesOffActualBytesOverLimitStillRejected() throws Exception {
        boolean originalDigest = client().isUploadDigestVerify();
        boolean originalSize = client().isUploadSizeVerify();
        long originalMax = client().getUploadMaxSize();
        try {
            client().setUploadDigestVerify(false);
            client().setUploadSizeVerify(false);
            client().setUploadMaxSize(10);
            // 不声明 size/digest（双关后均可省），实际 13 字节 > 上限 10 —— 内容层实际字节恒检必须兜住
            byte[] content = "0123456789abc".getBytes(StandardCharsets.UTF_8);
            JSecureUploadRequest overLimit = buildRequest(builder -> builder.entry(
                    pushEntry("file-cap", "invoice", "a.bin", null, null, null, null), content));
            perform(overLimit)
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                            Matchers.containsString("超出大小上限")));
            // 对照：同开关组合下未超限内容（不声明 size/digest）受理——双关不等于全拒，
            // 元数据/fileId/数量对齐/上限防线仍在（与文档"无内容校验"口径一致）
            byte[] small = "01234567".getBytes(StandardCharsets.UTF_8);
            JSecureUploadRequest within = buildRequest(builder -> builder.entry(
                    pushEntry("file-ok", "invoice", "a.bin", null, null, null, null), small));
            perform(within).andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
        } finally {
            client().setUploadDigestVerify(originalDigest);
            client().setUploadSizeVerify(originalSize);
            client().setUploadMaxSize(originalMax);
        }
    }

    @Test
    @DisplayName("digest 关 + size 开：size 必填与「声明=实际」比对不受 digest 开关影响（镜像独立性）")
    void digestOffSizeOnStillEnforcesSizePresenceAndMatch() throws Exception {
        boolean originalDigest = client().isUploadDigestVerify();
        try {
            client().setUploadDigestVerify(false);
            byte[] content = "mirror-independence".getBytes(StandardCharsets.UTF_8);
            // size 缺失：digest 开关关闭不豁免 size 必填
            JSecureUploadRequest noSize = buildRequest(builder -> builder.entry(
                    pushEntry("file-nosize", "invoice", "a.pdf", "pdf", null, null, null), content));
            perform(noSize)
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                            Matchers.containsString("缺少 size")));
            // 声明不符：比对仍生效
            JSecureUploadRequest wrongSize = buildRequest(builder -> builder.entry(
                    pushEntry("file-wrongsize", "invoice", "a.pdf", "pdf", (long) content.length + 1, null, null), content));
            perform(wrongSize)
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                            Matchers.containsString("大小与声明不符")));
        } finally {
            client().setUploadDigestVerify(originalDigest);
        }
    }

    // ------------------------------------------------------------------
    // 算法范围交互：空白条目 / 大小写 / 范围配置形态
    // ------------------------------------------------------------------

    @Test
    @DisplayName("终端配 [sm3] 时空白 digestAlg 条目应拒：null/纯空格均归一 sha256 后不在范围")
    void blankDigestAlgUnderSm3OnlyScopeRejected() throws Exception {
        List<String> original = client().getUploadDigestAlgorithms();
        try {
            client().setUploadDigestAlgorithms(List.of("sm3"));
            byte[] content = "blank-alg".getBytes(StandardCharsets.UTF_8);
            // digestAlg=null（fastjson2 省略字段，服务端 DTO 缺省 sha256）
            JSecureUploadRequest nullAlg = buildRequest(builder -> builder.entry(
                    pushEntry("f-null-alg", "invoice", "a.pdf", "pdf", (long) content.length,
                            JSecureUploadUtil.digestHex(content, "sha256"), null), content));
            perform(nullAlg)
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                            Matchers.containsString("摘要算法不在允许范围")));
            // digestAlg=纯空格（wire 上带 "   "，服务端 trim 后按空白归一 sha256）
            JSecureUploadRequest blankAlg = buildRequest(builder -> builder.entry(
                    pushEntry("f-blank-alg", "invoice", "a.pdf", "pdf", (long) content.length,
                            JSecureUploadUtil.digestHex(content, "sha256"), "   "), content));
            perform(blankAlg)
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                            Matchers.containsString("摘要算法不在允许范围")));
        } finally {
            client().setUploadDigestAlgorithms(original);
        }
    }

    @Test
    @DisplayName("大小写容错：条目 digestAlg=SHA256（大写）+ 摘要值大写带首尾空格 仍受理")
    void uppercaseAlgAndDigestValueAccepted() throws Exception {
        byte[] content = "case-tolerance".getBytes(StandardCharsets.UTF_8);
        String upperDigest = JSecureUploadUtil.digestHex(content, "sha256").toUpperCase();
        JSecureUploadRequest request = buildRequest(builder -> builder.entry(
                pushEntry("f-upper", "invoice", "a.pdf", "pdf", (long) content.length,
                        "  " + upperDigest + "  ", "SHA256"), content));
        perform(request).andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("终端范围配置大小写与空白容错：配置 '  SM3  ' + 条目 sm3（正确摘要）受理")
    void terminalScopeEntryCaseInsensitive() throws Exception {
        List<String> original = client().getUploadDigestAlgorithms();
        try {
            client().setUploadDigestAlgorithms(List.of("  SM3  "));
            byte[] content = "scope-case".getBytes(StandardCharsets.UTF_8);
            JSecureUploadRequest request = buildRequest(builder -> builder.entry(
                    pushEntry("f-scope-case", "invoice", "a.pdf", "pdf", (long) content.length,
                            JSecureUploadUtil.digestHex(content, "sm3"), "sm3"), content));
            perform(request).andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
        } finally {
            client().setUploadDigestAlgorithms(original);
        }
    }

    @Test
    @DisplayName("范围含协议外名 [md5, sha1]：sha1 条目仍被拒（当前延后至内容层、以全集口径报错——层间不一致见缺陷清单）")
    void bogusAlgInScopeRejectedWhenDigestOn() throws Exception {
        List<String> originalScope = client().getUploadDigestAlgorithms();
        try {
            client().setUploadDigestAlgorithms(List.of("md5", "sha1"));
            byte[] content = "bogus-scope".getBytes(StandardCharsets.UTF_8);
            JSecureUploadRequest request = buildRequest(builder -> builder.entry(
                    pushEntry("f-sha1", "invoice", "a.pdf", "pdf", (long) content.length,
                            "0123456789abcdef0123456789abcdef01234567", "sha1"), content));
            // 拒绝本身正确（fail-closed 未破）；但报错发生在内容层 digestHex，且消息"终端允许"列的是协议全集而非终端配置
            perform(request)
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                            Matchers.containsString("摘要算法不在允许范围")));
        } finally {
            client().setUploadDigestAlgorithms(originalScope);
        }
    }

    // ------------------------------------------------------------------
    // 客户端 digestAlg() 链式：add()/pull()/entry() 作用口径
    // ------------------------------------------------------------------

    @Test
    @DisplayName("digestAlg(SM3) 链式端到端：add() 按 sm3 计算并声明，终端范围 [sm3] 受理")
    void clientDigestAlgSm3ChainEndToEnd() throws Exception {
        List<String> original = client().getUploadDigestAlgorithms();
        try {
            client().setUploadDigestAlgorithms(List.of("sm3"));
            byte[] content = "client-sm3-chain".getBytes(StandardCharsets.UTF_8);
            // 大写 SM3 传入：客户端归一 sm3 → add() 摘要按 sm3 计算、digestAlg 声明 sm3 → 服务端范围与比对均过
            JSecureUploadRequest request = buildRequest(builder -> builder
                    .digestAlg("SM3")
                    .add(content, "f-client-sm3", "invoice", "a.pdf"));
            perform(request).andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
        } finally {
            client().setUploadDigestAlgorithms(original);
        }
    }

    @Test
    @DisplayName("entry() 全自定义不受 digestAlg() 影响：digestAlg(sm3) 下 entry 仍按自身 sha256 声明受理")
    void clientEntryIsolatedFromChannelDigestAlg() throws Exception {
        byte[] content = "entry-isolation".getBytes(StandardCharsets.UTF_8);
        // 若 digestAlg() 泄漏进 entry()，服务端会按 sm3 重算内容而声明摘要是 sha256 → 必拒；
        // 受理即证明 entry() 的 digest/digestAlg 完全由调用方自控
        JSecureUploadRequest request = buildRequest(builder -> builder
                .digestAlg("sm3")
                .entry(pushEntry("f-entry-own", "invoice", "a.pdf", "pdf", (long) content.length,
                        JSecureUploadUtil.digestHex(content, "sha256"), "sha256"), content));
        perform(request).andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("digestAlg(sm3) 流入 pull() 声明：范围 [sha256] 拒、范围 [sm3] 收（同 URL 同白名单）")
    void clientPullDeclaresChannelDigestAlg() throws Exception {
        List<String> originalScope = client().getUploadDigestAlgorithms();
        try {
            client().getUploadFetchUrlWhitelist().add("*.partner.com");
            // 范围 [sha256]：pull 条目被声明成 sm3 → 元数据层拒（证明 pull() 采用通道级 digestAlg）
            client().setUploadDigestAlgorithms(List.of("sha256"));
            JSecureUploadRequest asSha256 = buildRequest(builder -> builder
                    .digestAlg("sm3")
                    .pull("remote-alg", "https://cdn.partner.com/a.pdf", 3L, null, "invoice", "pdf"));
            perform(asSha256)
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                            Matchers.containsString("摘要算法不在允许范围")));
            // 范围 [sm3]：同请求受理
            client().setUploadDigestAlgorithms(List.of("sm3"));
            JSecureUploadRequest asSm3 = buildRequest(builder -> builder
                    .digestAlg("sm3")
                    .pull("remote-alg", "https://cdn.partner.com/a.pdf", 3L, null, "invoice", "pdf"));
            perform(asSm3).andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
        } finally {
            client().setUploadDigestAlgorithms(originalScope);
            client().getUploadFetchUrlWhitelist().clear();
        }
    }

    @Test
    @DisplayName("digestAlg() 非法名 fail-fast：sha1 在构建器上即抛协议异常")
    void clientDigestAlgInvalidNameFailsFast() {
        UploadChannel<SecureUploadApiTest.UploadOrderBiz, Void> channel = JSecureClient.of(client().getId())
                .upload(URL, new SecureUploadApiTest.UploadOrderBiz(), Void.class);
        assertThatThrownBy(() -> channel.digestAlg("sha1"))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("摘要算法不在允许范围");
    }

    // ------------------------------------------------------------------
    // pull 元数据：声明 size 上限/负值恒检（不受 size 开关豁免）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("pull 声明 size 恒检：超上限/负值即使 size 开关关闭也拒")
    void pullDeclaredSizeOverLimitOrNegativeRejected() throws Exception {
        boolean originalSize = client().isUploadSizeVerify();
        try {
            client().getUploadFetchUrlWhitelist().add("*.partner.com");
            client().setUploadSizeVerify(false);
            JSecureUploadRequest overLimit = buildRequest(builder -> builder
                    .pull("remote-size", "https://cdn.partner.com/a.pdf", Long.MAX_VALUE, null, "invoice", "pdf"));
            perform(overLimit)
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                            Matchers.containsString("size 非法或超出上限")));
            JSecureUploadRequest negative = buildRequest(builder -> builder
                    .pull("remote-size", "https://cdn.partner.com/a.pdf", -1L, null, "invoice", "pdf"));
            perform(negative)
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.message",
                            Matchers.containsString("size 非法或超出上限")));
        } finally {
            client().setUploadSizeVerify(originalSize);
            client().getUploadFetchUrlWhitelist().clear();
        }
    }

    // ------------------------------------------------------------------
    // 裁定落地：算法范围只约束 push/pull（真正使用摘要的模式），refer 不受范围牵连
    // ------------------------------------------------------------------

    @Test
    @DisplayName("裁定后口径：终端范围 [sm3] 时 refer() 条目仍受理（refer 不涉摘要，跳过范围校验走钩子）")
    void referUnderSm3OnlyScopeAccepted() throws Exception {
        List<String> original = client().getUploadDigestAlgorithms();
        try {
            client().setUploadDigestAlgorithms(List.of("sm3"));
            // refer() 构建的条目 digestAlg 为 DTO 缺省 "sha256"，但不参与摘要计算/比对，
            // 元数据层范围校验对 refer 豁免（与 format 白名单对 refer 不查同口径）
            JSecureUploadRequest request = buildRequest(builder -> builder.refer("arch-ref-1"));
            perform(request)
                    .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
        } finally {
            client().setUploadDigestAlgorithms(original);
        }
    }

    /** refer 钩子：arch-ref-1 视为存在（refer 豁免范围校验后仍走钩子存在性校验）。 */
    @org.springframework.boot.test.context.TestConfiguration
    static class ReferHookConfig {
        @org.springframework.context.annotation.Bean
        io.github.jevonsnotes.jevokit.api.upload.JSecureUploadFileRefChecker demoRefChecker() {
            return file -> java.util.Set.of("arch-ref-1").contains(file.getFileId());
        }
    }

    // ------------------------------------------------------------------
    // helpers（与 SecureUploadApiTest 同构）
    // ------------------------------------------------------------------

    private JSecureUploadRequest buildRequest(Consumer<UploadChannel<SecureUploadApiTest.UploadOrderBiz, Void>> customizer) {
        SecureUploadApiTest.UploadOrderBiz biz = new SecureUploadApiTest.UploadOrderBiz();
        biz.setOrderNo("SO-SWITCH-001");
        biz.setAmount(1.00);
        UploadChannel<SecureUploadApiTest.UploadOrderBiz, Void> channel = JSecureClient.of(client().getId())
                .upload(URL, biz, Void.class)
                .transNo(UUID.randomUUID().toString());
        customizer.accept(channel);
        return channel.prepare().getRequest();
    }

    private org.springframework.test.web.servlet.ResultActions perform(JSecureUploadRequest request) throws Exception {
        MockPart metaPart = new MockPart("meta", null,
                request.getMetaJson().getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON);
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(URL).part(metaPart);
        request.getHeaders().forEach(builder::header);
        request.getFiles().forEach(file -> builder.file(new org.springframework.mock.web.MockMultipartFile(
                "files", file.getFileId(), file.getContentType(), file.getContent())));
        return perform(builder);
    }

    private org.springframework.test.web.servlet.ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").exists());
    }

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
}
