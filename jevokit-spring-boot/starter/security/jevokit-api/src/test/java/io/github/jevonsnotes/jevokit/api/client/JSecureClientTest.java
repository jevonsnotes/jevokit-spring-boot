package io.github.jevonsnotes.jevokit.api.client;

import com.alibaba.fastjson2.JSON;
import com.sun.net.httpserver.HttpServer;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import io.github.jevonsnotes.jevokit.api.annotation.JSecureApi;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiConfiguration;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadRequest;
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
 * JSecureClient 统一门面测试：两通道（JSON post / multipart upload）happy path
 * （真加密签名 → 服务端解密验证往返）、transType 限定、自定义回调注入路径、
 * 响应验签失败抛异常，以及默认载体（hutool）的报文形态验收。
 *
 * <p>默认载体验收用本地 JDK HttpServer 桩接收 hutool 实发报文：
 * JSON 通道断言协议头 + application/json 体；上传通道断言 multipart 形态与服务端期望一致——
 * meta part 无 filename 且 Content-Type=application/json（服务端按"无 filename 即非文件 part"
 * 采集、按 content-type 路由消息转换器），文件 part filename=fileId（对齐键），
 * 桩按服务端同源配方回加密签名响应，全链验证解密验签。
 */
@WebMvcTest
@Import({RefreshAutoConfiguration.class, JevokitApiConfiguration.class, JSecureClientTest.FacadeController.class})
@TestPropertySource(properties = {
        "jevokit.api.client[0].id=jsc-test-client",
        "jevokit.api.client[0].secret-key=0123456789abcdef",
        // 演示 SM2 密钥对（与 sample application.yml 同源，仅用于测试）
        "jevokit.api.client[0].private-key=MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk5B6bcSXNwvtfd6nufsDHEPOu8N2hHKJMBW5blZP2eSgCgYIKoEcz1UBgi2hRANCAASxO58/zvEseRlMZtRlojzn7m4jAaZPTDSOalPCSk3nnGW0GmEu2ZhfWDNH+Nq8wA6BLodb2gwI9NF+F1Mp3p9R",
        "jevokit.api.client[0].public-key=MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEsTufP87xLHkZTGbUZaI85+5uIwGmT0w0jmpTwkpN55xltBphLtmYX1gzR/javMAOgS6HW9oMCPTRfhdTKd6fUQ==",
        "jevokit.api.client[0].verify=true",
        "jevokit.api.client[0].prevent-replay=false",
        "jevokit.api.client[0].decrypt=true",
        "jevokit.api.client[0].encrypt=true",
        "jevokit.api.client[0].fetch-url-whitelist[0]=*.partner.com",
})
@DisplayName("JSecureClient 统一门面")
class JSecureClientTest {

    private static final String CLIENT_ID = "jsc-test-client";
    private static final String SECRET = "0123456789abcdef";
    private static final String TRANS_TYPE = "TN-CLIENT-TEST";

    @Autowired
    MockMvc mockMvc;

    // ------------------------------------------------------------------
    // JSON 通道：happy path（真加密签名 → 服务端解密验证往返）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("JSON 通道 happy path：code=0、响应解密还原业务对象、transNo 回显")
    void jsonChannelHappyPathRoundTrips() {
        EchoPayload payload = new EchoPayload();
        payload.setName("facade-demo");
        JSecureResponse<EchoPayload> response = JSecureClient.of(CLIENT_ID)
                .post("/secure/echo", payload, EchoPayload.class)
                .transNo("t-001")
                .transType(TRANS_TYPE)
                .execute(this::postJsonViaMockMvc);

        assertThat(response.getCode()).as("合规请求应返回 code=0").isEqualTo("0");
        assertThat(response.getTarget()).as("响应应解密还原业务对象").isNotNull();
        assertThat(response.getTarget().getName()).isEqualTo("facade-demo");
        assertThat(response.getTransNo()).as("transNo 应回显请求值").isEqualTo("t-001");
    }

    @Test
    @DisplayName("transType 限定生效：报文交易类型与服务端声明不符时 code=999")
    void transTypeMismatchRejected() {
        JSecureResponse<EchoPayload> response = JSecureClient.of(CLIENT_ID)
                .post("/secure/echo", new EchoPayload(), EchoPayload.class)
                .transNo("t-type-mismatch")
                .transType("TN-WRONG")
                .execute(this::postJsonViaMockMvc);

        assertThat(response.getCode()).isEqualTo("999");
        assertThat(response.getMessage()).contains("交易类型不匹配");
    }

    @Test
    @DisplayName("响应验签失败抛异常：终端 verify=true 时签名不匹配不返回未验签数据")
    void responseSignatureVerifyFailureThrows() {
        // 桩响应：data 真加密、signature 却签的是别的内容（服务端私钥伪造场景）
        String view = "{\"name\":\"forged\"}";
        JSecureResponse<String> forged = new JSecureResponse<>();
        forged.setCode("0");
        forged.setView(view);
        forged.setSignature(SecurityUtil.sm2Base64Sign("other-content", PRIVATE_KEY_FOR_STUB));
        forged.setData(SecurityUtil.sm4cbcEncrypt(view, SECRET));

        JsonChannel<EchoPayload, EchoPayload> channel = JSecureClient.of(CLIENT_ID)
                .post("/secure/echo", new EchoPayload(), EchoPayload.class)
                .transNo("t-forge")
                .transType(TRANS_TYPE);
        assertThatThrownBy(() -> channel.execute((url, body, headers) -> JSON.toJSONString(forged)))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("返回结果验签失败");
    }

    // ------------------------------------------------------------------
    // 上传通道：happy path（push + pull 混合，自定义回调注入）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("上传通道 happy path：push+pull 混合经 MockMvc 受理，code=0 且响应还原")
    void uploadChannelHappyPathRoundTrips() {
        JSecureResponse<String> response = JSecureClient.of(CLIENT_ID)
                .upload("/secure/upload", Map.of("orderNo", "SO-2026-001"), String.class)
                .transNo("t-002")
                .transType("TN-CLIENT-TEST")
                .add("invoice-bytes".getBytes(StandardCharsets.UTF_8), "file-1", "invoice", "发票.pdf")
                .add("id-card-bytes".getBytes(StandardCharsets.UTF_8), "file-2", "id_card", "身份证.jpg")
                .pull("remote-1", "https://cdn.partner.com/contract.pdf", 3L, null, "invoice", "pdf")
                .execute(this::postMultipartViaMockMvc);

        assertThat(response.getCode()).as("push+pull 混合请求应受理 code=0").isEqualTo("0");
        assertThat(response.getTarget()).isEqualTo("accepted:3/2");
    }

    // ------------------------------------------------------------------
    // 默认载体（hutool）：报文形态验收（JDK HttpServer 桩，无 Spring 参与）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("默认载体 JSON 形态：协议头齐全 + application/json 体 + 响应解密验签")
    void defaultCarrierJsonWireForm() throws IOException {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        try (StubServer stub = StubServer.start(captured, "{\"name\":\"ok\"}")) {
            JSecureResponse<EchoPayload> response = JSecureClient.of(CLIENT_ID)
                    .post(stub.url("/secure/echo"), new EchoPayload(), EchoPayload.class)
                    .transNo("t-wire-json")
                    .transType(TRANS_TYPE)
                    .execute();

            assertThat(response.getCode()).as("桩按服务端配方回包，应解密验签通过").isEqualTo("0");
            assertThat(response.getTarget().getName()).isEqualTo("ok");

            CapturedRequest request = captured.get();
            assertThat(request.method).isEqualTo("POST");
            assertThat(request.contentType).startsWith("application/json");
            assertThat(request.headers).containsKeys("x-api-client-id", "x-api-timestamp", "x-api-nonce", "x-api-signature");
            // 请求体即协议信封（含 SM4 密文 data 与 SM2 signature），可被服务端配方还原
            assertThat(request.bodyText()).contains("\"transNo\":\"t-wire-json\"").contains("\"data\":").contains("\"signature\":");
        }
    }

    @Test
    @DisplayName("默认载体 multipart 形态：meta 无 filename 且 application/json、files filename=fileId")
    void defaultCarrierMultipartWireForm() throws IOException {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        try (StubServer stub = StubServer.start(captured, "\"accepted:2/1\"")) {
            JSecureResponse<String> response = JSecureClient.of(CLIENT_ID)
                    .upload(stub.url("/secure/upload"), Map.of("orderNo", "SO-1"), String.class)
                    .transNo("t-wire-upload")
                    .add("invoice-bytes".getBytes(StandardCharsets.UTF_8), "file-1", "invoice", "发票.pdf")
                    .pull("remote-1", "https://cdn.partner.com/a.pdf", 3L, null, "invoice", "pdf")
                    .execute();

            assertThat(response.getCode()).as("桩按服务端配方回包，应解密验签通过").isEqualTo("0");
            assertThat(response.getTarget()).isEqualTo("accepted:2/1");

            CapturedRequest request = captured.get();
            assertThat(request.method).isEqualTo("POST");
            assertThat(request.contentType).startsWith("multipart/form-data");
            assertThat(request.headers).containsKeys("x-api-client-id", "x-api-timestamp", "x-api-nonce", "x-api-signature");

            String multipart = request.bodyText();
            // meta part：name=meta 且不携带 filename（服务端按"无 filename 即非文件 part"采集，
            // 混入 filename 会被当作文件 part 计数导致数量不一致拒绝）
            assertThat(multipart)
                    .as("meta part 应为无 filename 的表单字段形态")
                    .contains("Content-Disposition: form-data; name=\"meta\"\r\n")
                    .doesNotContain("name=\"meta\"; filename=");
            // meta part 的 Content-Type 必须是 application/json（服务端按它路由消息转换器）
            assertThat(multipart).contains("Content-Type: application/json\r\n");
            // 文件 part：part 名恒为 files、filename=fileId（服务端按 filename 对齐摘要清单）
            assertThat(multipart).contains("name=\"files\"; filename=\"file-1\"");
            // push 文件字节明文在体
            assertThat(multipart).contains("invoice-bytes");
        }
    }

    // ------------------------------------------------------------------
    // helpers：MockMvc 传输层（自定义回调注入路径）
    // ------------------------------------------------------------------

    /** JSON 通道 MockMvc 载体：应用全部协议头、断言 HTTP 200 并返回响应体。 */
    private String postJsonViaMockMvc(String url, String json, Map<String, String> headers) {
        try {
            MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(url)
                    .content(json)
                    .contentType(MediaType.APPLICATION_JSON);
            headers.forEach(builder::header);
            return mockMvc.perform(builder)
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 上传通道 MockMvc 载体：meta part + 协议头 + 逐文件 part 并返回响应体。 */
    private String postMultipartViaMockMvc(String url, JSecureUploadRequest payload) {
        try {
            MockPart metaPart = new MockPart("meta", null,
                    payload.getMetaJson().getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON);
            MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(url).part(metaPart);
            payload.getHeaders().forEach(builder::header);
            payload.getFiles().forEach(file -> builder.file(new org.springframework.mock.web.MockMultipartFile(
                    "files", file.getFileId(), file.getContentType(), file.getContent())));
            return mockMvc.perform(builder)
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 桩侧伪造响应用的服务端私钥（与 TestPropertySource 演示密钥同对）。 */
    private static final String PRIVATE_KEY_FOR_STUB =
            "MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk5B6bcSXNwvtfd6nufsDHEPOu8N2hHKJMBW5blZP2eSgCgYIKoEcz1UBgi2hRANCAASxO58/zvEseRlMZtRlojzn7m4jAaZPTDSOalPCSk3nnGW0GmEu2ZhfWDNH+Nq8wA6BLodb2gwI9NF+F1Mp3p9R";

    /** 桩接收到的原始请求（默认载体形态断言用；header 键统一小写，屏蔽容器/客户端大小写规范化差异）。 */
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
     * 最小 HTTP 桩：捕获 hutool 实发请求，并按服务端同源配方
     * （SM4 加密 data + SM2 签名）回一个成功信封，验证门面响应解密验签链路。
     */
    private static final class StubServer implements AutoCloseable {
        private final HttpServer server;

        private StubServer(HttpServer server) {
            this.server = server;
        }

        static StubServer start(AtomicReference<CapturedRequest> captured, String view) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                CapturedRequest request = new CapturedRequest();
                request.method = exchange.getRequestMethod();
                request.contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                exchange.getRequestHeaders().forEach((key, value) -> request.headers.put(key.toLowerCase(), value));
                request.body = exchange.getRequestBody().readAllBytes();
                captured.set(request);

                // 服务端配方回包：view 明文 → SM2 签名 + SM4 加密（view/target 不出网）
                JSecureResponse<String> resp = new JSecureResponse<>();
                resp.setCode("0");
                resp.setMessage("ok");
                resp.setTransNo("t-stub");
                resp.setView(view);
                resp.setSignature(SecurityUtil.sm2Base64Sign(view, PRIVATE_KEY_FOR_STUB));
                resp.setData(SecurityUtil.sm4cbcEncrypt(view, SECRET));
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

    /** 回显载荷（Jackson/fastjson2 双侧序列化：需默认构造器 + 读写器）。 */
    public static class EchoPayload {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /** 被测端点：JSON 通道 /secure/echo（transType 限定）+ 上传通道 /secure/upload。 */
    @RestController
    static class FacadeController {

        @JSecureApi(transType = TRANS_TYPE)
        @PostMapping("/secure/echo")
        public JSecureResponse<EchoPayload> echo(@RequestBody @Valid JSecureRequest<EchoPayload> request) {
            EchoPayload res = new EchoPayload();
            res.setName(request.getTarget().getName());
            return JSecureResponse.ok(res);
        }

        @JSecureApi(transType = TRANS_TYPE)
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
