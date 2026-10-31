package io.github.jevonsnotes.jevokit.api.client;

import com.alibaba.fastjson2.JSON;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiConfiguration;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;
import io.github.jevonsnotes.jevokit.core.util.SecurityUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JSecureClient 默认载体（hutool）对异常 HTTP 响应的行为边界：
 * 非 200（500+HTML / 404+空体）、200 非 JSON（text/plain）——行为必须可预期且异常类型统一为
 * {@link JSecureApiException}；以及外发 multipart 报文形态安全边界：0 字节文件 part 不丢、
 * fileId 字符集白名单（引号/换行/中文等非法字符在入口 fail-fast 拒绝，不进入外发报文）。
 *
 * <p>桩：JDK HttpServer 按用例指定状态码与响应体；成功回包按服务端同源配方（SM4+SM2）构造。
 */
@WebMvcTest
@Import({RefreshAutoConfiguration.class, JevokitApiConfiguration.class})
@TestPropertySource(properties = {
        "jevokit.api.client[0].id=non200-client",
        "jevokit.api.client[0].secret-key=0123456789abcdef",
        // 演示 SM2 密钥对（与 sample application.yml 同源，仅用于测试）
        "jevokit.api.client[0].private-key=MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk5B6bcSXNwvtfd6nufsDHEPOu8N2hHKJMBW5blZP2eSgCgYIKoEcz1UBgi2hRANCAASxO58/zvEseRlMZtRlojzn7m4jAaZPTDSOalPCSk3nnGW0GmEu2ZhfWDNH+Nq8wA6BLodb2gwI9NF+F1Mp3p9R",
        "jevokit.api.client[0].public-key=MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEsTufP87xLHkZTGbUZaI85+5uIwGmT0w0jmpTwkpN55xltBphLtmYX1gzR/javMAOgS6HW9oMCPTRfhdTKd6fUQ==",
        "jevokit.api.client[0].verify=false",
        "jevokit.api.client[0].prevent-replay=false",
        "jevokit.api.client[0].decrypt=true",
        "jevokit.api.client[0].encrypt=true",
})
@DisplayName("JSecureClient 默认载体异常响应与报文形态边界")
class JSecureClientNon200CarrierTest {

    private static final String CLIENT_ID = "non200-client";
    private static final String SECRET = "0123456789abcdef";
    private static final String GATEWAY_502_HTML =
            "<html><head><title>502 Bad Gateway</title></head><body><center><h1>502 Bad Gateway</h1></center></body></html>";

    // ------------------------------------------------------------------
    // 非 200 响应：行为可预期 + 异常类型统一
    // ------------------------------------------------------------------

    @Test
    @DisplayName("500 + HTML：默认载体应抛 JSecureApiException（网关错误页不得以裸 fastjson/NPE 形态漏出）")
    void jsonChannel500HtmlResponseThrowsProtocolException() throws IOException {
        try (StubServer stub = StubServer.start(500, "text/html", GATEWAY_502_HTML.getBytes(StandardCharsets.UTF_8), null)) {
            assertThatThrownBy(() -> JSecureClient.of(CLIENT_ID)
                    .post(stub.url("/secure/echo"), Map.of("k", "v"), Object.class)
                    .transNo("t-500").execute())
                    .as("hutool 默认载体对非 2xx 不抛（body 原样返回），门面解析 HTML 必须转协议异常")
                    .isInstanceOf(JSecureApiException.class);
        }
    }

    @Test
    @DisplayName("404 + 空响应体：默认载体应抛 JSecureApiException（不得 NPE）")
    void jsonChannel404EmptyBodyThrowsProtocolException() throws IOException {
        try (StubServer stub = StubServer.start(404, "text/html", new byte[0], null)) {
            assertThatThrownBy(() -> JSecureClient.of(CLIENT_ID)
                    .post(stub.url("/secure/echo"), Map.of("k", "v"), Object.class)
                    .transNo("t-404").execute())
                    .as("空响应体（hutool body() 返回空串）解析为 null 信封后的取值不得裸 NPE")
                    .isInstanceOf(JSecureApiException.class);
        }
    }

    @Test
    @DisplayName("200 + 非 JSON（text/plain）：默认载体应抛 JSecureApiException")
    void jsonChannel200PlainTextViewThrowsProtocolException() throws IOException {
        try (StubServer stub = StubServer.start(200, "text/plain", "plain-ok".getBytes(StandardCharsets.UTF_8), null)) {
            assertThatThrownBy(() -> JSecureClient.of(CLIENT_ID)
                    .post(stub.url("/secure/echo"), Map.of("k", "v"), Object.class)
                    .transNo("t-plain").execute())
                    .isInstanceOf(JSecureApiException.class);
        }
    }

    @Test
    @DisplayName("500 + HTML（上传通道）：默认载体应抛 JSecureApiException")
    void uploadChannel500HtmlResponseThrowsProtocolException() throws IOException {
        try (StubServer stub = StubServer.start(500, "text/html", GATEWAY_502_HTML.getBytes(StandardCharsets.UTF_8), null)) {
            assertThatThrownBy(() -> JSecureClient.of(CLIENT_ID)
                    .upload(stub.url("/secure/upload"), Map.of("k", "v"), Object.class)
                    .transNo("t-500-up")
                    .add("bytes".getBytes(StandardCharsets.UTF_8), "file-1", "invoice", "发票.pdf")
                    .execute())
                    .isInstanceOf(JSecureApiException.class);
        }
    }

    // ------------------------------------------------------------------
    // 外发报文形态：0 字节文件 / 危险 fileId / 中文 fileId
    // ------------------------------------------------------------------

    @Test
    @DisplayName("0 字节 push 文件：默认载体 multipart 中文件 part 不丢（filename 存在且内容 0 字节）")
    void zeroBytePushFileStillEmitsFilePartOnWire() throws IOException {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        try (StubServer stub = StubServer.okCapture(captured)) {
            JSecureClient.of(CLIENT_ID)
                    .upload(stub.url("/secure/upload"), Map.of("k", "v"), Object.class)
                    .transNo("t-empty-file")
                    .add(new byte[0], "empty-1", "invoice", "空文件.pdf")
                    .execute();

            String multipart = captured.get().bodyText();
            assertThat(multipart).as("0 字节文件的 part 必须照常外发，否则服务端按数量不一致拒绝")
                    .contains("name=\"files\"; filename=\"empty-1\"");
            // part 头块结束（\r\n\r\n）后应紧跟边界终结符（0 字节内容 + 结尾 CRLF + -- 边界），
            // 即确实发出了"内容为空"的文件 part
            int filenameIdx = multipart.indexOf("filename=\"empty-1\"");
            int contentStart = multipart.indexOf("\r\n\r\n", filenameIdx) + 4;
            assertThat(multipart.substring(contentStart, Math.min(contentStart + 4, multipart.length())))
                    .as("空文件 part 的内容区应为 0 字节（紧跟 \\r\\n-- 边界）")
                    .isEqualTo("\r\n--");
        }
    }

    @Test
    @DisplayName("fileId 含双引号：不得原样写入 Content-Disposition 造成 filename 走私（转义或拒绝二选一）")
    void quoteInFileIdMustNotSmuggleFilenameOnWire() throws IOException {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        String evilFileId = "f1\"; filename=\"smuggled";
        try (StubServer stub = StubServer.okCapture(captured)) {
            try {
                JSecureClient.of(CLIENT_ID)
                        .upload(stub.url("/secure/upload"), Map.of("k", "v"), Object.class)
                        .transNo("t-quote")
                        .add("bytes".getBytes(StandardCharsets.UTF_8), evilFileId, "invoice", "发票.pdf")
                        .execute();
            } catch (JSecureApiException acceptable) {
                // 拒绝危险 fileId 也是合规实现（fail-fast）
                return;
            }
            String multipart = captured.get().bodyText();
            assertThat(multipart)
                    .as("含双引号的 fileId 原样写入 filename 会拆出第二个 filename 属性（part 走私/错位）")
                    .doesNotContain("smuggled");
        }
    }

    @Test
    @DisplayName("fileId 含 CRLF：不得原样写入造成 part 头注入（报文中不得出现注入的伪造头行）")
    void crlfInFileIdMustNotInjectPartHeaderOnWire() throws IOException {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        String evilFileId = "f1\r\nX-Evil-Header: 1";
        try (StubServer stub = StubServer.okCapture(captured)) {
            try {
                JSecureClient.of(CLIENT_ID)
                        .upload(stub.url("/secure/upload"), Map.of("k", "v"), Object.class)
                        .transNo("t-crlf")
                        .add("bytes".getBytes(StandardCharsets.UTF_8), evilFileId, "invoice", "发票.pdf")
                        .execute();
            } catch (JSecureApiException acceptable) {
                return;
            }
            String multipart = captured.get().bodyText();
            assertThat(multipart)
                    .as("CRLF 写入 Content-Disposition 会伪造出 X-Evil-Header 头行")
                    .doesNotContain("X-Evil-Header");
        }
    }

    @Test
    @DisplayName("中文 fileId：随 BUG-4/5 白名单收紧改为客户端 fail-fast 拒绝（不再 UTF-8 原样外发）")
    void chineseFileIdRejectedByClientWhitelist() throws IOException {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        try (StubServer stub = StubServer.okCapture(captured)) {
            // 随 BUG-4/5 fileId 字符集白名单收紧（仅 [A-Za-z0-9._-]，长度 1-128）：
            // 中文 fileId 从"可用"变"拒绝"，原断言（wire 上 UTF-8 原样保留）随行为收紧更新
            assertThatThrownBy(() -> JSecureClient.of(CLIENT_ID)
                    .upload(stub.url("/secure/upload"), Map.of("k", "v"), Object.class)
                    .transNo("t-cn-fileid")
                    .add("bytes".getBytes(StandardCharsets.UTF_8), "文件-001", "invoice", "发票.pdf")
                    .execute())
                    .as("中文 fileId 应在客户端入口被拒，不进入外发报文")
                    .isInstanceOf(JSecureApiException.class)
                    .hasMessageContaining("fileId");
            assertThat(captured.get()).as("请求不应外发").isNull();
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 桩接收到的原始请求。 */
    private static final class CapturedRequest {
        String method;
        String contentType;
        Map<String, List<String>> headers = new HashMap<>();
        byte[] body;

        String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /**
     * 最小 HTTP 桩：固定状态码/响应体回包并捕获请求；成功模式（200）按服务端同源配方回加密信封。
     */
    private static final class StubServer implements AutoCloseable {
        private final HttpServer server;

        private StubServer(HttpServer server) {
            this.server = server;
        }

        static StubServer start(int status, String contentType, byte[] body, String successView) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                // 丢弃入参请求体（形态断言用捕获模式）
                exchange.getRequestBody().readAllBytes();
                byte[] out = body;
                if (status == 200 && successView != null) {
                    JSecureResponse<String> resp = new JSecureResponse<>();
                    resp.setCode("0");
                    resp.setMessage("ok");
                    resp.setView(successView);
                    resp.setData(SecurityUtil.sm4cbcEncrypt(successView, SECRET));
                    out = JSON.toJSONString(resp).getBytes(StandardCharsets.UTF_8);
                }
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(status, out.length == 0 ? -1 : out.length);
                if (out.length > 0) {
                    exchange.getResponseBody().write(out);
                }
                exchange.close();
            });
            server.start();
            return new StubServer(server);
        }

        /** 成功回包 + 捕获外发请求（报文形态断言用）。 */
        static StubServer okCapture(AtomicReference<CapturedRequest> captured) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                CapturedRequest request = new CapturedRequest();
                request.method = exchange.getRequestMethod();
                request.contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                exchange.getRequestHeaders().forEach((key, value) -> request.headers.put(key.toLowerCase(), value));
                request.body = exchange.getRequestBody().readAllBytes();
                captured.set(request);

                JSecureResponse<String> resp = new JSecureResponse<>();
                resp.setCode("0");
                resp.setMessage("ok");
                resp.setView("\"ok\"");
                resp.setData(SecurityUtil.sm4cbcEncrypt("\"ok\"", SECRET));
                byte[] out = JSON.toJSONString(resp).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, out.length);
                exchange.getResponseBody().write(out);
                exchange.close();
            });
            server.start();
            return new StubServer(server);
        }

        String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
