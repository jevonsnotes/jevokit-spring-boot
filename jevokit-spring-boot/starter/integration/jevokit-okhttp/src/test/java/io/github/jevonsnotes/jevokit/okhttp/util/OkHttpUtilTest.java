package io.github.jevonsnotes.jevokit.okhttp.util;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.jevonsnotes.jevokit.core.exception.IORuntimeException;
import io.github.jevonsnotes.jevokit.core.exception.OkHttpStatusRuntimeException;
import io.github.jevonsnotes.jevokit.okhttp.config.OkhttpProperties;
import io.github.jevonsnotes.jevokit.okhttp.dto.FormDataPart;

import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OkHttpUtil 行为单测（B 档测试重构 #5）：基于 okhttp 官方 MockWebServer，
 * 直接构造 OkHttpUtil（不依赖 Spring 容器），覆盖 URL 构建、请求头、表单、
 * multipart、代理选择、超时、错误码与错误模式路径。无外部环境依赖。
 */
class OkHttpUtilTest {

    private MockWebServer server;
    private OkHttpUtil util;
    private OkhttpProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        properties = new OkhttpProperties();
        util = newUtil(new OkHttpClient(), new OkHttpClient());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private OkHttpUtil newUtil(OkHttpClient plainClient, OkHttpClient proxyClient) {
        return new OkHttpUtil(properties, plainClient, proxyClient);
    }

    private String url(String path) {
        return server.url(path).toString();
    }

    private RecordedRequest take() throws InterruptedException {
        return server.takeRequest(3, TimeUnit.SECONDS);
    }

    // ---------------- GET / URL / 头 ----------------

    @Test
    @DisplayName("get：基础 GET 返回响应体，方法与路径正确")
    void get_returnsBodyAndUsesGetMethod() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("hello-kit"));
        String body = util.get(url("/get"));

        assertThat(body).as("GET 应返回响应体").isEqualTo("hello-kit");
        RecordedRequest recorded = take();
        assertThat(recorded.getMethod()).as("请求方法应为 GET").isEqualTo("GET");
        assertThat(recorded.getPath()).as("请求路径应保留").startsWith("/get");
    }

    @Test
    @DisplayName("getWithParams：参数经 URLEncoder 拼接到查询串")
    void get_paramsAppendedToQuery() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("ok"));
        Map<String, String> params = new LinkedHashMap<>();
        params.put("k1", "v1");
        params.put("k2", "v a");

        util.getWithParams(url("/p"), params);

        RecordedRequest recorded = take();
        assertThat(recorded.getPath()).as("查询参数应按键序拼接并 URL 编码")
                .contains("k1=v1")
                .contains("k2=v+a");
    }

    @Test
    @DisplayName("getWithHeaders：自定义头随请求发送")
    void get_headersSent() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("ok"));
        util.getWithHeaders(url("/h"), Map.of("X-Kit-Trace", "abc-123"));

        RecordedRequest recorded = take();
        assertThat(recorded.getHeader("X-Kit-Trace")).as("自定义请求头应送达服务端").isEqualTo("abc-123");
    }

    @Test
    @DisplayName("getByte/download：二进制响应逐字节一致")
    void getByte_returnsExactBytes() {
        byte[] payload = {0x00, 0x01, 0x7F, (byte) 0xFF, 0x42};
        server.enqueue(new MockResponse().setBody(new okio.Buffer().write(payload)));

        byte[] got = util.getByte(url("/bin"));

        assertThat(got).as("二进制内容应无损坏").isEqualTo(payload);
    }

    @Test
    @DisplayName("buildUrlByQueryParams：默认开启 URL 编码")
    void buildUrl_encodesByDefault() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("a", "1");
        params.put("b", "2+2");

        String built = OkHttpUtil.buildUrlByQueryParams("http://kit.example/p", params);

        assertThat(built).as("默认 urlEncode=true，+ 与特殊字符应被编码")
                .isEqualTo("http://kit.example/p?a=1&b=2%2B2");
    }

    @Test
    @DisplayName("buildUrlByQueryParams：urlEncode=false 原样拼接")
    void buildUrl_noEncodeWhenDisabled() {
        String built = OkHttpUtil.buildUrlByQueryParams("http://kit.example/p",
                Map.of("a", "1 2"), false, StandardCharsets.UTF_8);

        assertThat(built).as("关闭编码时应原样拼接").isEqualTo("http://kit.example/p?a=1 2");
    }

    @Test
    @DisplayName("buildUrlByQueryParams：空参数时 url 原样返回")
    void buildUrl_emptyParamsKeepsUrl() {
        assertThat(OkHttpUtil.buildUrlByQueryParams("http://kit.example/x", null))
                .as("参数为 null 时不应追加问号").isEqualTo("http://kit.example/x");
    }

    @Test
    @DisplayName("parseParams：百分号编码解码为 map（+ 原样保留），空串返回空 map")
    void parseParams_decodesAndHandlesBlank() {
        Map<String, String> decoded = OkHttpUtil.parseParams("a=1&b=2%2B3");

        assertThat(decoded).as("解码结果应含全部键值（hutool 语义：+ 不转空格）")
                .containsEntry("a", "1")
                .containsEntry("b", "2+3");
        assertThat(OkHttpUtil.parseParams("")).as("空输入应返回空 map").isEmpty();
        assertThat(OkHttpUtil.parseParams(null)).as("null 输入应返回空 map").isEmpty();
    }

    // ---------------- POST：表单 / json / xml / 自定义体 / 字符集 ----------------

    @Test
    @DisplayName("postForm：表单字段与 Content-Type 正确")
    void postForm_fieldsAndContentType() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("ok"));
        Map<String, String> params = new LinkedHashMap<>();
        params.put("a", "1");
        params.put("b", "2");

        util.postForm(url("/form"), params);

        RecordedRequest recorded = take();
        assertThat(recorded.getHeader("Content-Type")).as("表单 Content-Type 应为 urlencoded")
                .startsWith("application/x-www-form-urlencoded");
        assertThat(recorded.getBody().readUtf8()).as("表单体应为 k=v 拼接").isEqualTo("a=1&b=2");
    }

    @Test
    @DisplayName("post（指定字符集）：非默认字符集表单值按该字符集编码发送（BUG-5 修复后行为）")
    void post_formValuesEncodedWithGivenCharset() throws Exception {
        server.enqueue(new MockResponse().setBody("ok"));
        Map<String, String> params = new LinkedHashMap<>();
        params.put("word", "中文测试");
        params.put("emoji", "测试😀");

        util.post(url("/form-gbk"), params, null, Charset.forName("GBK"));

        RecordedRequest recorded = take();
        // okhttp FormBody 的 Content-Type 头未内联 charset 参数（okhttp 4.x FormBody.contentType() 行为），
        // 字符集实际编码在 body 字节里，故此处只断言 urlencoded 基型
        assertThat(recorded.getHeader("Content-Type"))
                .as("表单 Content-Type 应为 urlencoded（FormBody 4.x 不回显 charset）")
                .startsWith("application/x-www-form-urlencoded");
        // 修复前：平台默认编码 getBytes() 再按 GBK 解码再按 UTF-8 重编码，中文必然乱码；
        // 修复后：值按 GBK 编码直送
        String body = new String(recorded.getBody().readByteArray(), Charset.forName("GBK"));
        assertThat(body).as("GBK 表单值应按 GBK 解码后与原值一致")
                .contains("word=" + java.net.URLEncoder.encode("中文测试", "GBK"))
                .contains("emoji=" + java.net.URLEncoder.encode("测试😀", "GBK"));
    }

    @Test
    @DisplayName("postJson：JSON 体与 Content-Type 正确")
    void postJson_contentTypeAndBody() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("ok"));
        String json = "{\"k\":1}";

        util.postJson(url("/json"), json);

        RecordedRequest recorded = take();
        assertThat(recorded.getHeader("Content-Type")).as("JSON Content-Type 应带 charset")
                .isEqualTo("application/json; charset=UTF-8");
        assertThat(recorded.getBody().readUtf8()).as("请求体应原样送达").isEqualTo(json);
    }

    @Test
    @DisplayName("postXml：XML 体与 Content-Type 正确")
    void postXml_contentTypeAndBody() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("ok"));
        String xml = "<a>1</a>";

        util.postXml(url("/xml"), xml);

        RecordedRequest recorded = take();
        assertThat(recorded.getHeader("Content-Type")).as("XML Content-Type 应带 charset（okhttp 头部字符集统一小写）")
                .isEqualToIgnoringCase("application/xml; charset=UTF-8");
        assertThat(recorded.getBody().readUtf8()).as("请求体应原样送达").isEqualTo(xml);
    }

    @Test
    @DisplayName("postBody：自定义 contentType 自动补 charset")
    void postBody_customContentTypeGetsCharset() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("ok"));

        util.postBody(url("/raw"), "abc", "text/plain", StandardCharsets.UTF_8);

        RecordedRequest recorded = take();
        assertThat(recorded.getHeader("Content-Type")).as("未含 charset 的 contentType 应被补全")
                .isEqualTo("text/plain; charset=UTF-8");
        assertThat(recorded.getBody().readUtf8()).isEqualTo("abc");
    }

    @Test
    @DisplayName("post：指定字符集解码响应体")
    void post_decodesResponseWithGivenCharset() {
        byte[] gbkBytes = "中文内容".getBytes(Charset.forName("GBK"));
        server.enqueue(new MockResponse().setBody(new okio.Buffer().write(gbkBytes)));

        String body = util.post(url("/gbk"), null, Charset.forName("GBK"));

        assertThat(body).as("GBK 响应应按指定字符集正确解码").isEqualTo("中文内容");
    }

    @Test
    @DisplayName("postFormDataPart：multipart 含文本字段与文件 part")
    void postFormDataPart_multipartSent(@TempDir File tempDir) throws Exception {
        server.enqueue(new MockResponse().setBody("ok"));
        File file = new File(tempDir, "part.txt");
        Files.writeString(file.toPath(), "file-content-中文");

        FormDataPart part = FormDataPart.builder()
                .name("attachment")
                .fileName("part.txt")
                .file(file)
                .mediaType("text/plain")
                .build();

        util.postFormDataPart(url("/multi"), Map.of("field", "fv"), java.util.List.of(part));

        RecordedRequest recorded = take();
        assertThat(recorded.getHeader("Content-Type")).as("应为 multipart 表单")
                .startsWith("multipart/form-data");
        String body = recorded.getBody().readUtf8();
        assertThat(body).as("文本字段应存在于 multipart 体").contains("name=\"field\"").contains("fv");
        assertThat(body).as("文件 part 应带文件名与内容")
                .contains("name=\"attachment\"")
                .contains("filename=\"part.txt\"")
                .contains("file-content-中文");
    }

    // ---------------- 错误码 / 错误模式 ----------------

    @Test
    @DisplayName("httpStatusSensitive=true：非 2xx 抛 OkHttpStatusRuntimeException")
    void errorStatus_sensitiveThrows() {
        properties.setHttpStatusSensitive(true);
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        assertThatThrownBy(() -> util.get(url("/err")))
                .as("敏感模式下 500 应抛状态异常")
                .isInstanceOf(OkHttpStatusRuntimeException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("httpStatusSensitive=false（默认）quiet：GET/POST 非 2xx 均返回错误体（BUG-3 修复后统一契约）")
    void errorStatus_insensitiveQuietReturnsErrorBody() {
        properties.setErrorMode(OkhttpProperties.ErrorMode.quiet);
        server.enqueue(new MockResponse().setResponseCode(404).setBody("not found"));
        String getBody = util.get(url("/miss"));
        assertThat(getBody).as("quiet 模式下 GET 404 应返回错误体").isEqualTo("not found");

        server.enqueue(new MockResponse().setResponseCode(500).setBody("server boom"));
        String postBody = util.postForm(url("/miss"), Map.of("a", "1"));
        assertThat(postBody).as("quiet 模式下 POST 500 应同样返回错误体（与 GET 一致）").isEqualTo("server boom");
    }

    @Test
    @DisplayName("httpStatusSensitive=false（默认）throwout：非 2xx 抛 OkHttpStatusRuntimeException 并带响应信息（BUG-3 修复后）")
    void errorStatus_insensitiveThrowoutThrows() {
        properties.setErrorMode(OkhttpProperties.ErrorMode.throwout);
        server.enqueue(new MockResponse().setResponseCode(503).setBody("svc down"));

        assertThatThrownBy(() -> util.get(url("/throw")))
                .as("throwout 模式下 GET 非 2xx 应抛状态异常")
                .isInstanceOf(OkHttpStatusRuntimeException.class)
                .hasMessageContaining("503")
                .hasMessageContaining("svc down");

        // POST 路径同样尊重 error-mode（修复前返回空串）
        server.enqueue(new MockResponse().setResponseCode(404).setBody("gone"));
        assertThatThrownBy(() -> util.postForm(url("/throw2"), null))
                .as("throwout 模式下 POST 非 2xx 应抛状态异常（与 GET 一致）")
                .isInstanceOf(OkHttpStatusRuntimeException.class)
                .hasMessageContaining("404")
                .hasMessageContaining("gone");
    }

    @Test
    @DisplayName("errorMode=log_simple/log_detail：非 2xx 不抛、记录日志后返回错误体")
    void errorStatus_logModesReturnErrorBody() {
        properties.setErrorMode(OkhttpProperties.ErrorMode.log_simple);
        server.enqueue(new MockResponse().setResponseCode(404).setBody("nf"));

        String body = util.get(url("/logsimple"));
        // log 模式在 processError 只打日志不抛——状态异常被吞后回落返回错误体
        assertThat(body).as("log_simple 模式下应记录并返回错误体").isEqualTo("nf");
    }

    @Test
    @DisplayName("errorMode=throwout（默认）：连接失败抛 IORuntimeException")
    void ioError_throwoutModeThrows() throws IOException {
        server.shutdown();

        assertThatThrownBy(() -> util.get(url("/dead")))
                .as("默认 throwout 模式下 IO 失败应抛运行时异常")
                .isInstanceOf(IORuntimeException.class);
    }

    @Test
    @DisplayName("errorMode=quiet：连接失败静默返回空串")
    void ioError_quietModeReturnsEmpty() throws IOException {
        properties.setErrorMode(OkhttpProperties.ErrorMode.quiet);
        server.shutdown();

        String body = util.get(url("/dead"));

        assertThat(body).as("quiet 模式下 IO 失败不应抛出，返回空串").isEmpty();
    }

    // ---------------- 重定向 / 超时 / 代理 ----------------

    @Test
    @DisplayName("getRedirectUrl：302 返回 Location 头")
    void redirectUrl_returnsLocationHeader() {
        OkHttpClient noFollow = new OkHttpClient.Builder().followRedirects(false).build();
        util = newUtil(noFollow, noFollow);
        server.enqueue(new MockResponse().setResponseCode(302)
                .setHeader("Location", url("/newplace")));

        String redirect = util.getRedirectUrl(url("/old"));

        assertThat(redirect).as("应返回重定向目标地址").isEqualTo(url("/newplace"));
    }

    @Test
    @DisplayName("读超时：throwout 模式抛 IORuntimeException")
    void readTimeout_throwsInThrowoutMode() {
        OkHttpClient slowRead = new OkHttpClient.Builder()
                .readTimeout(150, TimeUnit.MILLISECONDS)
                .build();
        util = newUtil(slowRead, slowRead);
        server.enqueue(new MockResponse().setBody("late").setBodyDelay(2, TimeUnit.SECONDS));

        assertThatThrownBy(() -> util.get(url("/slow")))
                .as("读超时应按 throwout 模式抛出")
                .isInstanceOf(IORuntimeException.class);
    }

    @Test
    @DisplayName("proxyMode=always：请求经代理客户端发出（绝对形式 URI）")
    void proxyModeAlways_requestGoesThroughProxy() throws InterruptedException {
        OkHttpClient proxyClient = new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.HTTP,
                        new java.net.InetSocketAddress(server.getHostName(), server.getPort())))
                .build();
        properties.setProxyMode(OkhttpProperties.ProxyMode.always);
        util = newUtil(new OkHttpClient(), proxyClient);
        server.enqueue(new MockResponse().setBody("via-proxy"));

        String body = util.get("http://fake.target.example/x");

        assertThat(body).as("代理请求应正常取回响应").isEqualTo("via-proxy");
        RecordedRequest recorded = take();
        // 注意：不使用 RecordedRequest.getRequestUrl()——本机 hosts 将 dbeaver.com 映射到 127.0.0.1，
        // 反向解析污染了 mockwebserver 的 URL 重建；请求行原文才是代理目标的直接证据
        assertThat(recorded.getRequestLine()).as("经 HTTP 代理的请求行应为绝对形式 URI")
                .startsWith("GET ")
                .contains("http://fake.target.example/x");
    }

    @Test
    @DisplayName("downloadAsStream：返回活流可完整读取，关闭后连接归还（BUG-4 修复后行为）")
    void downloadAsStream_returnsReadableStreamAndClosesResponse() throws Exception {
        server.enqueue(new MockResponse().setBody("stream-body"));

        // when：方法返回时 Response 已不再被提前关闭，流持有其生命周期
        try (java.io.InputStream in = util.downloadAsStream(url("/dl"))) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // then：拿到即可读，内容完整（修复前读取即抛 IOException——Response 已关）
            assertThat(content).as("返回的流应可完整读取").isEqualTo("stream-body");
        }
        take();
    }

    @Test
    @DisplayName("downloadAsStream：提前 close 不泄漏，重复 close 安全（幂等）")
    void downloadAsStream_earlyCloseIsSafeAndIdempotent() throws Exception {
        server.enqueue(new MockResponse().setBody("x"));

        java.io.InputStream in = util.downloadAsStream(url("/dl2"));
        assertThat(in).as("应返回非空流对象").isNotNull();
        // 未读取直接关闭：不应抛异常（FilterInputStream.close + response.close 均幂等）
        in.close();
        in.close();
        take();
    }

    @Test
    @DisplayName("postDownloadAsStream：POST 流式下载同样可读（同一 executeStream 路径）")
    void postDownloadAsStream_readable() throws Exception {
        server.enqueue(new MockResponse().setBody("post-stream"));

        try (java.io.InputStream in = util.postDownloadAsStream(url("/pdl"), Map.of("k", "v"), null)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .as("POST 流式下载应可完整读取").isEqualTo("post-stream");
        }
        RecordedRequest recorded = take();
        assertThat(recorded.getMethod()).as("请求方法应为 POST").isEqualTo("POST");
        assertThat(recorded.getBody().readUtf8()).as("表单参数应随请求发送").contains("k=v");
    }
}
