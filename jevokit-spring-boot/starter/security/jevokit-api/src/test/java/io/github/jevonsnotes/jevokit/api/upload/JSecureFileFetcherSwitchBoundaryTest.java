package io.github.jevonsnotes.jevokit.api.upload;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.dto.FetchMode;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDetailDto;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JSecureFileFetcher 开关组合矩阵补充用例（测试工程师独立复核）：
 * 现有 {@code JSecureFileFetcherTest} 全部跑缺省开关（digest/size 校验均开），
 * 未覆盖 upload-digest-verify / upload-size-verify 关闭后的语义与 readCapped 封顶分支：
 * <ul>
 *   <li>digest 关：错摘要跳过复核；</li>
 *   <li>size 关：声明 size 谎报跳过比对（按终端上限封顶放行全量）；</li>
 *   <li>未声明 size / size 关：readCapped 按终端 upload-max-size 封顶（内存自保，非协议拒绝）；</li>
 *   <li>声明小于实际：走「实际 = 声明」比对分支（现有用例只盖了超封顶中止分支）；</li>
 *   <li>双关组合：无任何内容校验，仅剩白名单 + 上限封顶防线；</li>
 *   <li>摘要复核的算法范围/大小写口径（与入口校验同源 normalizeDigestAlg）。</li>
 * </ul>
 */
@DisplayName("pull 拉取器开关矩阵边界")
class JSecureFileFetcherSwitchBoundaryTest {

    private static final byte[] PAYLOAD = "remote-invoice-bytes".getBytes(StandardCharsets.UTF_8);

    private static HttpServer server;
    private static String okUrl;

    private final JSecureFileFetcher fetcher = new JSecureFileFetcher();

    @BeforeAll
    static void startSourceServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            exchange.sendResponseHeaders(200, PAYLOAD.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(PAYLOAD);
            }
        });
        server.start();
        okUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/ok";
    }

    @AfterAll
    static void stopSourceServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // digest 开关
    // ------------------------------------------------------------------

    @Test
    @DisplayName("digest 关 + 错摘要：跳过复核，取回字节")
    void digestVerifyOffSkipsDigestRecheck() {
        JevokitApiClientProperties client = whitelistedClient();
        client.setUploadDigestVerify(false);
        byte[] content = fetcher.fetch(pullEntry(okUrl, (long) PAYLOAD.length, "deadbeef", "sha256"), client);
        assertThat(content).isEqualTo(PAYLOAD);
    }

    @Test
    @DisplayName("digest 开 + 算法大写/摘要大写带空格：trim + 忽略大小写后复核通过")
    void digestVerifyOnUppercaseAlgAndValueAccepted() {
        String upper = JSecureUploadUtil.digestHex(PAYLOAD, "sha256").toUpperCase();
        byte[] content = fetcher.fetch(pullEntry(okUrl, (long) PAYLOAD.length, "  " + upper + "  ", "SHA256"),
                whitelistedClient());
        assertThat(content).isEqualTo(PAYLOAD);
    }

    @Test
    @DisplayName("digest 开 + 算法范围：终端 [sm3] 下 sha256 复核按范围拒")
    void digestAlgOutOfScopeRejectedAtFetchRecheck() {
        JevokitApiClientProperties client = whitelistedClient();
        client.setUploadDigestAlgorithms(List.of("sm3"));
        assertThatThrownBy(() -> fetcher.fetch(pullEntry(okUrl, (long) PAYLOAD.length,
                JSecureUploadUtil.digestHex(PAYLOAD, "sha256"), "sha256"), client))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("摘要算法不在允许范围");
    }

    @Test
    @DisplayName("digest 开 + sm3 范围：sm3 摘要复核通过（范围与算法一致路径）")
    void sm3ScopeFetchRecheckRoundTrip() {
        JevokitApiClientProperties client = whitelistedClient();
        client.setUploadDigestAlgorithms(List.of("sm3"));
        byte[] content = fetcher.fetch(pullEntry(okUrl, (long) PAYLOAD.length,
                JSecureUploadUtil.digestHex(PAYLOAD, "sm3"), "sm3"), client);
        assertThat(content).isEqualTo(PAYLOAD);
    }

    // ------------------------------------------------------------------
    // size 开关与 readCapped 封顶分支
    // ------------------------------------------------------------------

    @Test
    @DisplayName("size 关 + 声明 3 实际 20：跳过一致性比对，按终端上限封顶取回全量")
    void sizeVerifyOffLyingSizeAccepted() {
        JevokitApiClientProperties client = whitelistedClient();
        client.setUploadSizeVerify(false);
        byte[] content = fetcher.fetch(pullEntry(okUrl, 3L, null, "sha256"), client);
        assertThat(content).isEqualTo(PAYLOAD);
    }

    @Test
    @DisplayName("size 关 + 未声明 + 实际超终端上限：按 upload-max-size 封顶中止（内存自保恒生效）")
    void sizeVerifyOffOversizedResponseCappedByMaxSize() {
        JevokitApiClientProperties client = whitelistedClient();
        client.setUploadSizeVerify(false);
        client.setUploadMaxSize(5);
        assertThatThrownBy(() -> fetcher.fetch(pullEntry(okUrl, null, null, "sha256"), client))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("终端可接受上限");
    }

    @Test
    @DisplayName("size 开 + 未声明 size：不受比对影响，仅按终端上限封顶（声明可空语义）")
    void sizeVerifyOnUndeclaredSizeOnlyCappedByMaxSize() {
        JevokitApiClientProperties client = whitelistedClient();
        byte[] content = fetcher.fetch(pullEntry(okUrl, null, null, "sha256"), client);
        assertThat(content).isEqualTo(PAYLOAD);
        // 上限封顶同样生效：未声明 size 也不是拖垮内存的口子
        client.setUploadMaxSize(5);
        assertThatThrownBy(() -> fetcher.fetch(pullEntry(okUrl, null, null, "sha256"), client))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("终端可接受上限");
    }

    @Test
    @DisplayName("size 开 + 声明大于实际：走「实际=声明」比对分支拒（补现有用例只盖超封顶中止的缝隙）")
    void sizeVerifyOnOversizedDeclarationRejectedByComparison() {
        assertThatThrownBy(() -> fetcher.fetch(pullEntry(okUrl, 100L, null, "sha256"), whitelistedClient()))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("文件大小与声明不符")
                .hasMessageContaining("100")
                .hasMessageContaining(String.valueOf(PAYLOAD.length));
    }

    @Test
    @DisplayName("声明超终端上限 / 负值：下载前预检即拒（上限与合法性恒检，不发起下载）")
    void declaredOverMaxOrNegativeRejectedBeforeDownload() {
        JevokitApiClientProperties client = whitelistedClient();
        assertThatThrownBy(() -> fetcher.fetch(pullEntry(okUrl, Long.MAX_VALUE, null, "sha256"), client))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("超出大小上限");
        assertThatThrownBy(() -> fetcher.fetch(pullEntry(okUrl, -1L, null, "sha256"), client))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("size 非法");
    }

    // ------------------------------------------------------------------
    // 双关组合
    // ------------------------------------------------------------------

    @Test
    @DisplayName("双关组合：错摘要 + 谎报 size 均受理，仅剩白名单与上限封顶防线（与文档口径一致）")
    void bothSwitchesOffLyingEverythingAccepted() {
        JevokitApiClientProperties client = whitelistedClient();
        client.setUploadDigestVerify(false);
        client.setUploadSizeVerify(false);
        byte[] content = fetcher.fetch(pullEntry(okUrl, 3L, "deadbeef", "sha1"), client);
        assertThat(content).isEqualTo(PAYLOAD);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 白名单放行本地源站，其余全走缺省（digest/size 校验开、50MB 上限）。 */
    private JevokitApiClientProperties whitelistedClient() {
        JevokitApiClientProperties client = new JevokitApiClientProperties();
        client.setId("switch-test");
        client.getUploadFetchUrlWhitelist().add("127.0.0.1");
        return client;
    }

    private JSecureUploadFileDetailDto pullEntry(String url, Long size, String digest, String digestAlg) {
        JSecureUploadFileDetailDto entry = new JSecureUploadFileDetailDto();
        entry.setFileId("remote-1");
        entry.setFetchMode(FetchMode.pull);
        entry.setUrl(url);
        entry.setSize(size);
        entry.setDigest(digest);
        entry.setDigestAlg(digestAlg);
        return entry;
    }
}
