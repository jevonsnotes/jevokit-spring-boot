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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JSecureFileFetcher 单元测试：以 JDK HttpServer 起本地源站（127.0.0.1 随机端口）实证
 * 白名单双道复核、fetchToken 透传、size 预检封顶、digest 复核、重定向拒绝与非 pull 拒绝。
 */
@DisplayName("pull 文件拉取器")
class JSecureFileFetcherTest {

    private static final byte[] PAYLOAD = "remote-invoice-bytes".getBytes(StandardCharsets.UTF_8);

    private static HttpServer server;
    private static String okUrl;
    private static String redirectUrl;
    private static String tokenRequiredUrl;
    private static final AtomicBoolean TOKEN_SEEN = new AtomicBoolean(false);

    private final JSecureFileFetcher fetcher = new JSecureFileFetcher();

    @BeforeAll
    static void startSourceServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> respond(exchange, 200, PAYLOAD));
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://evil.com/steal");
            respond(exchange, 302, new byte[0]);
        });
        server.createContext("/token", exchange -> {
            TOKEN_SEEN.set(exchange.getRequestHeaders().getFirst(JSecureFileFetcher.FETCH_TOKEN_HEADER) != null);
            respond(exchange, TOKEN_SEEN.get() ? 200 : 403, TOKEN_SEEN.get() ? PAYLOAD : new byte[0]);
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        okUrl = base + "/ok";
        redirectUrl = base + "/redirect";
        tokenRequiredUrl = base + "/token";
    }

    @AfterAll
    static void stopSourceServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("白名单未配置必拒（fail-fast）")
    void fetchWithoutWhitelistRejected() {
        JevokitApiClientProperties noWhitelist = new JevokitApiClientProperties();
        noWhitelist.setId("no-whitelist-client");
        assertThatThrownBy(() -> fetcher.fetch(pullEntry(okUrl, null, null), noWhitelist))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("不在白名单");
    }

    @Test
    @DisplayName("白名单外地址必拒")
    void fetchOutOfWhitelistRejected() {
        assertThatThrownBy(() -> fetcher.fetch(pullEntry("http://evil.com/x", null, null), clientWithWhitelist()))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("不在白名单");
    }

    @Test
    @DisplayName("正向：域名内 + digest 复核通过，返回字节")
    void fetchHappyPath() {
        String digest = JSecureUploadUtil.digestHex(PAYLOAD, "sha256");
        byte[] content = fetcher.fetch(pullEntry(okUrl, (long) PAYLOAD.length, digest), clientWithWhitelist());
        assertThat(content).isEqualTo(PAYLOAD);
    }

    @Test
    @DisplayName("fetchToken 透传：源站无令牌即 403，带令牌取回内容")
    void fetchTokenPassedThrough() {
        JSecureUploadFileDetailDto entry = pullEntry(tokenRequiredUrl, (long) PAYLOAD.length, null);
        entry.setFetchToken("secret-token-123456");
        byte[] content = fetcher.fetch(entry, clientWithWhitelist());
        assertThat(TOKEN_SEEN).as("源站应收到 X-Fetch-Token 头").isTrue();
        assertThat(content).isEqualTo(PAYLOAD);
    }

    @Test
    @DisplayName("digest 不符必拒（源站内容被换）")
    void fetchDigestMismatchRejected() {
        assertThatThrownBy(() -> fetcher.fetch(pullEntry(okUrl, (long) PAYLOAD.length, "deadbeef"), clientWithWhitelist()))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("摘要校验失败");
    }

    @Test
    @DisplayName("size 谎报必拒：实际响应大于声明 size，封顶中止")
    void fetchSizeLieRejected() {
        assertThatThrownBy(() -> fetcher.fetch(pullEntry(okUrl, 3L, null), clientWithWhitelist()))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("超出声明");
    }

    @Test
    @DisplayName("重定向必拒：源站 302 到白名单外，不跟随")
    void fetchRedirectRejected() {
        assertThatThrownBy(() -> fetcher.fetch(pullEntry(redirectUrl, null, null), clientWithWhitelist()))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("响应码");
    }

    @Test
    @DisplayName("非 pull 条目必拒（fetch 只处理 pull 模式）")
    void fetchNonPullRejected() {
        JSecureUploadFileDetailDto pushEntry = new JSecureUploadFileDetailDto();
        pushEntry.setFileId("f1");
        pushEntry.setFetchMode(FetchMode.push);
        assertThatThrownBy(() -> fetcher.fetch(pushEntry, clientWithWhitelist()))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("仅支持拉取 pull");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] content) throws java.io.IOException {
        exchange.sendResponseHeaders(status, content.length == 0 ? -1 : content.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(content);
        }
    }

    /** 白名单固定放行 127.0.0.1（本地测试源站）。 */
    private JevokitApiClientProperties clientWithWhitelist() {
        JevokitApiClientProperties client = new JevokitApiClientProperties();
        client.setId("test");
        client.getUploadFetchUrlWhitelist().add("127.0.0.1");
        return client;
    }

    private JSecureUploadFileDetailDto pullEntry(String url, Long size, String digest) {
        JSecureUploadFileDetailDto entry = new JSecureUploadFileDetailDto();
        entry.setFileId("remote-1");
        entry.setFetchMode(FetchMode.pull);
        entry.setUrl(url);
        entry.setSize(size);
        entry.setDigest(digest);
        entry.setDigestAlg("sha256");
        return entry;
    }
}
