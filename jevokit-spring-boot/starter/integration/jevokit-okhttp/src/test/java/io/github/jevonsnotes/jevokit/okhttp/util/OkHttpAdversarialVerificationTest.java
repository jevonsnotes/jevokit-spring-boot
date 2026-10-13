package io.github.jevonsnotes.jevokit.okhttp.util;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.github.jevonsnotes.jevokit.core.exception.OkHttpStatusRuntimeException;
import io.github.jevonsnotes.jevokit.okhttp.config.OkhttpProperties;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BUG-3/4/5 对抗性复测（终验 #6）：独立于开发自测 OkHttpUtilTest，只补对抗增量——
 * 开发用例覆盖"常见态"，本类专攻边界态与"修复可能错"的假设。
 *
 * <p>覆盖点（协调方终验追加三条 + 增量）：
 * <ol>
 *   <li><b>BUG-3 补</b>：throwout 模式下 3xx 不误抛——followRedirects=true（okhttp 默认）时
 *       中间 302 被自动跟随、最终 200 不应判为失败；对抗面：followRedirects=false 时裸 302
 *       必须抛（code=302 且携带 URL），证明"不误抛"不是"全不抛"；</li>
 *   <li><b>BUG-3 增</b>：throwout 异常的访问器断言（code/url/body 三要素齐备，eng 用例只断
 *       message 包含）；</li>
 *   <li><b>BUG-4 补</b>：consumer 读流一半抛异常——try-with-resources 的 close 在异常路径
 *       也要走到、连接归池（后续请求可正常服务），而非只验证正常 close；</li>
 *   <li><b>BUG-4 增</b>：非 2xx 流路径语义随 error-mode 统一后的两条边界——throwout（默认）
 *       抛 OkHttpStatusRuntimeException 且连接即时释放；quiet 返回 null（吞错语义归位 quiet 模式）
 *       且连接即时释放，两用例均防回归；</li>
 *   <li><b>BUG-5 补</b>：ISO-8859-1 单字节字符集表单往返（排除"只有双字节字符集才暴露"的侥幸），
 *       另补默认 UTF-8 下 emoji 精确往返（eng 用例 GBK+emoji 中 emoji 必然降级为 '?'，未证真）。</li>
 * </ol>
 */
@DisplayName("BUG-3/4/5 复测：错误模式边界 + 流生命周期 + 表单字符集（对抗性）")
class OkHttpAdversarialVerificationTest {

    private MockWebServer server;
    private OkhttpProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        properties = new OkhttpProperties(); // errorMode 默认 throwout
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private OkHttpUtil util(OkHttpClient client) {
        return new OkHttpUtil(properties, client, client);
    }

    private String url(String path) {
        return server.url(path).toString();
    }

    // ------------------------------------------------------------------
    // BUG-3 补：throwout 下 3xx 的误抛边界
    // ------------------------------------------------------------------

    @Test
    @DisplayName("throwout + 自动跟随重定向：中间 302 被跟随，最终 200 不误抛")
    void throwoutDoesNotThrowWhenRedirectAutoFollowed() {
        // given：默认 client（followRedirects=true），302 → 同服务器 /land，随后 200
        OkHttpUtil util = util(new OkHttpClient());
        server.enqueue(new MockResponse().setResponseCode(302)
                .setHeader("Location", url("/land")));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("landed"));

        // when / then：调用方拿到的是跟随后的最终响应体，全程无异常
        String body = util.get(url("/hop"));
        assertThat(body).as("自动跟随重定向后应返回最终响应体").isEqualTo("landed");
    }

    @Test
    @DisplayName("throwout + 禁用跟随：裸 302 必须抛，code=302 且异常携带请求 URL")
    void throwoutThrowsOnRawRedirectWhenFollowDisabled() {
        // given：显式禁用重定向跟随（调用方要看裸响应），服务端回 302
        OkHttpClient noFollow = new OkHttpClient.Builder().followRedirects(false).build();
        OkHttpUtil util = util(noFollow);
        server.enqueue(new MockResponse().setResponseCode(302)
                .setHeader("Location", url("/elsewhere")));

        // when / then：302 是"调用方可见的最终响应"，throwout 应抛并携带定位信息
        assertThatThrownBy(() -> util.get(url("/raw-hop")))
                .as("禁用跟随时裸 302 应按非 2xx 抛出（不是全不抛）")
                .isInstanceOf(OkHttpStatusRuntimeException.class)
                .hasFieldOrPropertyWithValue("code", 302)
                .hasFieldOrPropertyWithValue("url", url("/raw-hop"));
    }

    @Test
    @DisplayName("throwout 异常三要素：code / url / body 访问器均携带（不只 message 拼接）")
    void throwoutExceptionCarriesCodeUrlBody() {
        OkHttpUtil util = util(new OkHttpClient());
        server.enqueue(new MockResponse().setResponseCode(500).setBody("leaked-detail"));

        assertThatThrownBy(() -> util.get(url("/five-hundred")))
                .isInstanceOf(OkHttpStatusRuntimeException.class)
                .hasFieldOrPropertyWithValue("code", 500)
                .hasFieldOrPropertyWithValue("body", "leaked-detail")
                .hasFieldOrPropertyWithValue("url", url("/five-hundred"));
    }

    // ------------------------------------------------------------------
    // BUG-4 补：流生命周期的异常路径
    // ------------------------------------------------------------------

    @Test
    @DisplayName("consumer 读一半抛异常：close 在异常路径执行、连接归池，后续请求正常")
    void streamHalfReadThenConsumerThrowsStillReleasesConnection() throws Exception {
        OkHttpUtil util = util(new OkHttpClient());
        // 8KB 响应体：足够读到一半后中途放弃
        server.enqueue(new MockResponse().setBody("x".repeat(8192)));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("next-ok"));

        // when：try-with-resources 内读 16 字节后 consumer 抛业务异常 → close 必经异常路径
        assertThatThrownBy(() -> {
            try (InputStream in = util.downloadAsStream(url("/half"))) {
                assertThat(in).isNotNull();
                byte[] chunk = new byte[16];
                int read = in.read(chunk);
                assertThat(read).as("流应可读").isEqualTo(16);
                throw new IllegalStateException("consumer-boom");
            }
        }).as("业务异常应原样穿透").hasMessage("consumer-boom");

        // then：连接已归池/释放——同一 server 立即接受并服务下一个请求
        String next = util.get(url("/after-half"));
        assertThat(next).as("异常路径 close 后连接应可复用").isEqualTo("next-ok");
    }

    @Test
    @DisplayName("非 2xx 流路径 throwout：抛 OkHttpStatusRuntimeException（三路径统一）且连接即时释放")
    void streamNon2xxThrowsInThrowoutModeAndReleasesConnection() throws Exception {
        OkHttpUtil util = util(new OkHttpClient());
        server.enqueue(new MockResponse().setResponseCode(404).setBody("gone"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("recovered"));

        // when / then：默认 throwout 下流路径非 2xx 也抛状态异常（与文本/字节路径统一），
        // 且抛出路径上响应已关闭——同一 server 立即接受并服务下一个请求佐证连接未泄漏
        assertThatThrownBy(() -> util.downloadAsStream(url("/miss-stream")))
                .as("throwout 模式下流路径非 2xx 应抛状态异常（三路径语义一致）")
                .isInstanceOf(OkHttpStatusRuntimeException.class)
                .hasFieldOrPropertyWithValue("code", 404);
        assertThat(util.get(url("/after-miss"))).isEqualTo("recovered");
    }

    @Test
    @DisplayName("非 2xx 流路径 quiet：返回 null 且连接即时释放（吞错语义归位 quiet 模式）")
    void streamNon2xxReturnsNullInQuietModeAndReleasesConnection() throws Exception {
        properties.setErrorMode(OkhttpProperties.ErrorMode.quiet);
        OkHttpUtil util = util(new OkHttpClient());
        server.enqueue(new MockResponse().setResponseCode(404).setBody("gone"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("recovered"));

        // when
        InputStream in = util.downloadAsStream(url("/miss-stream"));
        // then：quiet 吞错返回 null（不抛）；连接释放可由后续请求正常服务佐证
        assertThat(in).as("quiet 模式下流路径非 2xx 应返回 null").isNull();
        assertThat(util.get(url("/after-miss"))).isEqualTo("recovered");
    }

    // ------------------------------------------------------------------
    // BUG-5 补：单字节字符集 + 默认 UTF-8 emoji 精确往返
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ISO-8859-1 单字节字符集表单：é/ü/ñ 编码往返精确")
    void formIso8859SingleByteCharsetRoundTrip() throws Exception {
        OkHttpUtil util = util(new OkHttpClient());
        server.enqueue(new MockResponse().setBody("ok"));
        Map<String, String> params = Map.of(
                "city", "München",
                "name", "José ñ");

        // when
        util.post(url("/form-latin1"), params, null, Charset.forName("ISO-8859-1"));

        // then：服务端按 ISO-8859-1 解码表单体，值与原串逐字一致
        //（注：okhttp FormBody 将空格编码为 %20 而 URLEncoder 为 +，故对记录体做 URLDecoder 解码后比对，
        //  实测形态 name=Jos%E9%20%F1&city=M%FCnchen —— é/ü/ñ 单字节编码往返精确）
        RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);
        String body = new String(recorded.getBody().readByteArray(), Charset.forName("ISO-8859-1"));
        assertThat(body)
                .contains("city=" + URLEncoder.encode("München", "ISO-8859-1").replace("+", "%20"))
                .contains("name=" + URLEncoder.encode("José ñ", "ISO-8859-1").replace("+", "%20"));
    }

    @Test
    @DisplayName("默认 UTF-8 表单：emoji 与中文逐字精确往返（单双字节混合真值）")
    void formUtf8EmojiRoundTripExact() throws Exception {
        OkHttpUtil util = util(new OkHttpClient());
        server.enqueue(new MockResponse().setBody("ok"));
        String mixed = "中文😀🎉mix";
        Map<String, String> params = Map.of("word", mixed);

        // when：postForm 默认字符集（UTF-8）
        util.postForm(url("/form-utf8"), params);

        // then
        RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);
        String body = new String(recorded.getBody().readByteArray(), StandardCharsets.UTF_8);
        assertThat(body)
                .as("默认 UTF-8 下 emoji/中文表单值应逐字还原（URL 编码形态）")
                .contains("word=" + URLEncoder.encode(mixed, StandardCharsets.UTF_8.name()));
    }
}
