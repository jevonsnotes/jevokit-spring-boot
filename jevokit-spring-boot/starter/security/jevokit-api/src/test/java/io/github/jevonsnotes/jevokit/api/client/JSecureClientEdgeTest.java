package io.github.jevonsnotes.jevokit.api.client;

import com.alibaba.fastjson2.JSON;
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
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiProperties;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadRequest;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JSecureClient 门面边界测试（独立于 JSecureClientTest 的 happy path）：
 * 终端未登记 fail-fast 的异常类型、非法响应体（网关 502 HTML / 截断 JSON）的协议异常统一性、
 * 业务失败信封（code!=0）的调用方语义、encrypt/decrypt/verify 开关组合往返（全关/只验签/错配）、
 * transNo 不设的自动生成兜底、中文业务数据 UTF-8 往返。
 *
 * <p>断言原则：按"应然"断言（协议链路的一切失败应以 JSecureApiException 暴露、消息可定位），
 * 现状不满足即为缺陷记录，不为现状迁就。
 */
@WebMvcTest
@Import({RefreshAutoConfiguration.class, JevokitApiConfiguration.class, JSecureClientEdgeTest.EdgeController.class})
@TestPropertySource(properties = {
        "jevokit.api.client[0].id=edge-client",
        "jevokit.api.client[0].secret-key=0123456789abcdef",
        // 演示 SM2 密钥对（与 sample application.yml 同源，仅用于测试）
        "jevokit.api.client[0].private-key=MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk5B6bcSXNwvtfd6nufsDHEPOu8N2hHKJMBW5blZP2eSgCgYIKoEcz1UBgi2hRANCAASxO58/zvEseRlMZtRlojzn7m4jAaZPTDSOalPCSk3nnGW0GmEu2ZhfWDNH+Nq8wA6BLodb2gwI9NF+F1Mp3p9R",
        "jevokit.api.client[0].public-key=MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEsTufP87xLHkZTGbUZaI85+5uIwGmT0w0jmpTwkpN55xltBphLtmYX1gzR/javMAOgS6HW9oMCPTRfhdTKd6fUQ==",
        "jevokit.api.client[0].verify=true",
        "jevokit.api.client[0].prevent-replay=false",
        "jevokit.api.client[0].decrypt=true",
        "jevokit.api.client[0].encrypt=true",
})
@DisplayName("JSecureClient 门面边界")
class JSecureClientEdgeTest {

    private static final String CLIENT_ID = "edge-client";
    private static final String TRANS_TYPE = "TN-EDGE";
    /** 网关 502 典型 HTML 错误页。 */
    private static final String GATEWAY_502_HTML =
            "<html><head><title>502 Bad Gateway</title></head><body><center><h1>502 Bad Gateway</h1></center></body></html>";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JevokitApiProperties properties;

    private JevokitApiClientProperties client() {
        return properties.getClient().get(0);
    }

    // ------------------------------------------------------------------
    // 边界 1：终端 clientId 未登记（prepare fail-fast 的异常类型统一性）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("终端未登记必拒：JSON 通道 prepare 应抛协议异常 JSecureApiException（可诊断，非裸 RuntimeException）")
    void unregisteredClientIdJsonChannelPrepareThrowsProtocolException() {
        assertThatThrownBy(() -> JSecureClient.of("ghost-client")
                .post("/secure/echo", new EdgePayload(), EdgePayload.class)
                .transNo("t-ghost").transType(TRANS_TYPE)
                .prepare())
                .as("终端未登记属部署/配置错误，应走协议统一异常（含终端识别码，便于定位）")
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("ghost-client");
    }

    @Test
    @DisplayName("终端未登记必拒：上传通道 prepare 应抛协议异常 JSecureApiException")
    void unregisteredClientIdUploadChannelPrepareThrowsProtocolException() {
        assertThatThrownBy(() -> JSecureClient.of("ghost-client")
                .upload("/secure/upload", Map.of("k", "v"), Void.class)
                .transNo("t-ghost").transType(TRANS_TYPE)
                .prepare())
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("ghost-client");
    }

    // ------------------------------------------------------------------
    // 边界 2：非法响应体（网关 502 HTML / 截断 JSON）——异常类型必须统一
    // ------------------------------------------------------------------

    @Test
    @DisplayName("非法响应体（502 HTML）：JSON 通道 execute 应抛 JSecureApiException（协议统一异常）")
    void htmlResponseJsonChannelThrowsProtocolException() {
        JsonChannel<EdgePayload, EdgePayload> channel = JSecureClient.of(CLIENT_ID)
                .post("/secure/echo", new EdgePayload(), EdgePayload.class)
                .transNo("t-html").transType(TRANS_TYPE);
        assertThatThrownBy(() -> channel.execute((url, body, headers) -> GATEWAY_502_HTML))
                .as("网关回 HTML 时调用方应收到协议异常而非 fastjson 裸异常")
                .isInstanceOf(JSecureApiException.class);
    }

    @Test
    @DisplayName("非法响应体（截断 JSON）：JSON 通道 execute 应抛 JSecureApiException")
    void truncatedJsonResponseJsonChannelThrowsProtocolException() {
        // 在 data 字符串值中间截断，任何宽松解析都无法还原为合法信封
        String truncated = "{\"code\":\"0\",\"message\":\"ok\",\"transNo\":\"t-1\",\"data\":\"QUJD";
        JsonChannel<EdgePayload, EdgePayload> channel = JSecureClient.of(CLIENT_ID)
                .post("/secure/echo", new EdgePayload(), EdgePayload.class)
                .transNo("t-trunc").transType(TRANS_TYPE);
        assertThatThrownBy(() -> channel.execute((url, body, headers) -> truncated))
                .isInstanceOf(JSecureApiException.class);
    }

    @Test
    @DisplayName("非法响应体（502 HTML）：上传通道 execute 应抛 JSecureApiException")
    void htmlResponseUploadChannelThrowsProtocolException() {
        UploadChannel<Map<String, Object>, Void> channel = JSecureClient.of(CLIENT_ID)
                .upload("/secure/upload", Map.<String, Object>of("k", "v"), Void.class)
                .transNo("t-html").transType(TRANS_TYPE)
                .add("bytes".getBytes(StandardCharsets.UTF_8), "file-1", "invoice", "发票.pdf");
        assertThatThrownBy(() -> channel.execute((url, payload) -> GATEWAY_502_HTML))
                .as("上传通道与 JSON 通道共用 handleResponse，非法响应体异常类型应同样统一")
                .isInstanceOf(JSecureApiException.class);
    }

    // ------------------------------------------------------------------
    // 边界 3：code != 0 业务失败信封——调用方判定失败的语义可用性
    // ------------------------------------------------------------------

    @Test
    @DisplayName("业务失败信封：服务端回 FAIL（data 空）时 code/message 可读、target 为 null、不抛异常")
    void businessFailEnvelopeSemanticsReadable() {
        JSecureResponse<EdgePayload> response = JSecureClient.of(CLIENT_ID)
                .post("/secure/biz-fail", new EdgePayload(), EdgePayload.class)
                .transNo("t-biz-fail").transType(TRANS_TYPE)
                .execute(this::postJsonViaMockMvc);

        assertThat(response.getCode()).as("调用方应以 code 判定失败").isEqualTo("999");
        assertThat(response.getMessage()).as("失败原因应可读可定位").contains("余额不足");
        assertThat(response.getTarget()).as("data 为空的失败信封 target 必须为 null").isNull();
        assertThat(response.getView()).isNull();
        assertThat(response.getTransNo()).as("transNo 应回显便于对账").isEqualTo("t-biz-fail");
    }

    @Test
    @DisplayName("业务失败信封（上传通道）：FAIL 信封同样不抛异常且 code 可判定")
    void businessFailEnvelopeUploadChannelSemanticsReadable() {
        JSecureResponse<String> response = JSecureClient.of(CLIENT_ID)
                .upload("/secure/upload-biz-fail", Map.of("k", "v"), String.class)
                .transNo("t-biz-fail-up").transType(TRANS_TYPE)
                .add("bytes".getBytes(StandardCharsets.UTF_8), "file-1", "invoice", "发票.pdf")
                .execute(this::postMultipartViaMockMvc);

        assertThat(response.getCode()).isEqualTo("999");
        assertThat(response.getMessage()).contains("余额不足");
        assertThat(response.getTarget()).isNull();
    }

    // ------------------------------------------------------------------
    // 边界 4：encrypt/decrypt/verify 开关组合（全关/只验签/错配必拒），两通道各验
    // ------------------------------------------------------------------

    @Test
    @DisplayName("开关全关（encrypt/decrypt/verify=false）：JSON 通道明文往返语义正确")
    void allFlagsOffJsonRoundTrips() {
        withClient(c -> {
            c.setEncrypt(false);
            c.setDecrypt(false);
            c.setVerify(false);
        }, () -> {
            EdgePayload payload = new EdgePayload();
            payload.setName("plain-round-trip");
            JSecureResponse<EdgePayload> response = JSecureClient.of(CLIENT_ID)
                    .post("/secure/echo", payload, EdgePayload.class)
                    .transNo("t-plain").transType(TRANS_TYPE)
                    .execute(this::postJsonViaMockMvc);
            assertThat(response.getCode()).as("全关闭时明文+无验签往返应成功").isEqualTo("0");
            assertThat(response.getTarget()).isNotNull();
            assertThat(response.getTarget().getName()).isEqualTo("plain-round-trip");
            assertThat(response.getView()).as("decrypt=false 时 view 应为原文 data").isEqualTo(response.getData());
        });
    }

    @Test
    @DisplayName("开关全关：上传通道明文往返语义正确")
    void allFlagsOffUploadRoundTrips() {
        withClient(c -> {
            c.setEncrypt(false);
            c.setDecrypt(false);
            c.setVerify(false);
        }, () -> {
            JSecureResponse<String> response = JSecureClient.of(CLIENT_ID)
                    .upload("/secure/upload", Map.of("订单号", "SO-明文-001"), String.class)
                    .transNo("t-plain-up").transType(TRANS_TYPE)
                    .add("bytes".getBytes(StandardCharsets.UTF_8), "file-1", "invoice", "发票.pdf")
                    .execute(this::postMultipartViaMockMvc);
            assertThat(response.getCode()).isEqualTo("0");
            assertThat(response.getTarget()).isEqualTo("accepted:1/1");
        });
    }

    @Test
    @DisplayName("只验签（encrypt/decrypt=false，verify=true）：明文传输 + SM2 签名完整性校验往返正确")
    void verifyOnlyJsonRoundTrips() {
        withClient(c -> {
            c.setEncrypt(false);
            c.setDecrypt(false);
            c.setVerify(true);
        }, () -> {
            EdgePayload payload = new EdgePayload();
            payload.setName("integrity-only");
            JSecureResponse<EdgePayload> response = JSecureClient.of(CLIENT_ID)
                    .post("/secure/echo", payload, EdgePayload.class)
                    .transNo("t-verify-only").transType(TRANS_TYPE)
                    .execute(this::postJsonViaMockMvc);
            assertThat(response.getCode()).as("明文+签名（不加密）组合应支持完整往返").isEqualTo("0");
            assertThat(response.getTarget().getName()).isEqualTo("integrity-only");
        });
    }

    @Test
    @DisplayName("只验签：上传通道明文 + 签名校验往返正确")
    void verifyOnlyUploadRoundTrips() {
        withClient(c -> {
            c.setEncrypt(false);
            c.setDecrypt(false);
            c.setVerify(true);
        }, () -> {
            JSecureResponse<String> response = JSecureClient.of(CLIENT_ID)
                    .upload("/secure/upload", Map.of("k", "v"), String.class)
                    .transNo("t-verify-only-up").transType(TRANS_TYPE)
                    .add("bytes".getBytes(StandardCharsets.UTF_8), "file-1", "invoice", "发票.pdf")
                    .execute(this::postMultipartViaMockMvc);
            assertThat(response.getCode()).isEqualTo("0");
            assertThat(response.getTarget()).isEqualTo("accepted:1/1");
        });
    }

    @Test
    @DisplayName("错配（encrypt=true/decrypt=false）：服务端按密文当明文解析必拒，失败可诊断（code=999）")
    void mismatchEncryptWithoutDecryptRejectedDiagnosably() {
        withClient(c -> {
            c.setEncrypt(true);
            c.setDecrypt(false);
            c.setVerify(false);
        }, () -> {
            JSecureResponse<EdgePayload> response = JSecureClient.of(CLIENT_ID)
                    .post("/secure/echo", new EdgePayload(), EdgePayload.class)
                    .transNo("t-mismatch-1").transType(TRANS_TYPE)
                    .execute(this::postJsonViaMockMvc);
            assertThat(response.getCode()).as("客户端加密而服务端不解密属配置错配，必须拒绝且可诊断")
                    .isEqualTo("999");
            assertThat(response.getMessage()).as("失败消息应非空可定位").isNotBlank();
        });
    }

    @Test
    @DisplayName("错配（encrypt=false/decrypt=true）：服务端解密明文必失败，code=999 消息含「接口解密失败」")
    void mismatchDecryptWithoutEncryptRejectedDiagnosably() {
        withClient(c -> {
            c.setEncrypt(false);
            c.setDecrypt(true);
            c.setVerify(false);
        }, () -> {
            JSecureResponse<EdgePayload> response = JSecureClient.of(CLIENT_ID)
                    .post("/secure/echo", new EdgePayload(), EdgePayload.class)
                    .transNo("t-mismatch-2").transType(TRANS_TYPE)
                    .execute(this::postJsonViaMockMvc);
            assertThat(response.getCode()).isEqualTo("999");
            assertThat(response.getMessage()).contains("接口解密失败");
        });
    }

    // ------------------------------------------------------------------
    // 边界 5：transNo 不设（客户端自动生成兜底）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("transNo 不设：客户端自动生成 UUID 兜底（服务端 @NotBlank 恒可过），code=0 且 transNo 回显非空")
    void transNoOmittedAutoGeneratedByClient() {
        // 随商榷点 1（transNo 语义矛盾）修复：原断言"服务端 @NotBlank 拒绝 code=999"为旧行为，
        // 通道层不设 transNo 时自动生成，javadoc 所述"不设则自动生成"与实际一致
        JSecureResponse<EdgePayload> response = JSecureClient.of(CLIENT_ID)
                .post("/secure/echo", new EdgePayload(), EdgePayload.class)
                .transType(TRANS_TYPE)
                .execute(this::postJsonViaMockMvc);
        assertThat(response.getCode()).as("不设 transNo 应由客户端兜底生成而非被服务端拒绝").isEqualTo("0");
        assertThat(response.getTransNo()).as("自动生成的 transNo 应回显便于对账").isNotBlank();
    }

    @Test
    @DisplayName("transNo 不设（上传通道）：同样自动生成，code=0 且 transNo 回显非空")
    void transNoOmittedUploadAutoGeneratedByClient() {
        // 随商榷点 1（transNo 语义矛盾）修复：原断言"服务端拒绝 code=999"为旧行为，随行为变更更新
        JSecureResponse<String> response = JSecureClient.of(CLIENT_ID)
                .upload("/secure/upload", Map.of("k", "v"), String.class)
                .transType(TRANS_TYPE)
                .add("bytes".getBytes(StandardCharsets.UTF_8), "file-1", "invoice", "发票.pdf")
                .execute(this::postMultipartViaMockMvc);
        assertThat(response.getCode()).isEqualTo("0");
        assertThat(response.getTransNo()).isNotBlank();
    }

    // ------------------------------------------------------------------
    // 边界 6：中文业务数据 UTF-8 往返
    // ------------------------------------------------------------------

    @Test
    @DisplayName("中文业务数据往返：JSON 通道字段值含中文/emoji 不损坏")
    void chineseBusinessDataRoundTripsJsonChannel() {
        EdgePayload payload = new EdgePayload();
        payload.setName("合同编号-深圳-2026-№1-💰");
        JSecureResponse<EdgePayload> response = JSecureClient.of(CLIENT_ID)
                .post("/secure/echo", payload, EdgePayload.class)
                .transNo("t-中文-001").transType(TRANS_TYPE)
                .execute(this::postJsonViaMockMvc);
        assertThat(response.getCode()).isEqualTo("0");
        assertThat(response.getTarget().getName())
                .as("UTF-8 全链路（fastjson 序列化→SM4→服务端还原→响应加密→解密反序列化）不应损坏中文")
                .isEqualTo("合同编号-深圳-2026-№1-💰");
    }

    @Test
    @DisplayName("中文业务数据往返：上传通道业务键与文件名含中文不损坏")
    void chineseBusinessDataRoundTripsUploadChannel() {
        JSecureResponse<String> response = JSecureClient.of(CLIENT_ID)
                .upload("/secure/upload", Map.of("订单号", "SO-中文-001", "客户", "深圳市南山区"), String.class)
                .transNo("t-中文-002").transType(TRANS_TYPE)
                .add("发票内容".getBytes(StandardCharsets.UTF_8), "file-1", "invoice", "电子发票-十月.pdf")
                .execute(this::postMultipartViaMockMvc);
        assertThat(response.getCode()).isEqualTo("0");
        assertThat(response.getTarget()).isEqualTo("accepted:1/1");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 以指定开关组合执行（结束后恢复全开默认，防止污染同上下文其他用例）。 */
    private void withClient(Consumer<JevokitApiClientProperties> mutator, Runnable body) {
        JevokitApiClientProperties c = client();
        boolean encrypt = c.isEncrypt();
        boolean decrypt = c.isDecrypt();
        boolean verify = c.isVerify();
        try {
            mutator.accept(c);
            body.run();
        } finally {
            c.setEncrypt(encrypt);
            c.setDecrypt(decrypt);
            c.setVerify(verify);
        }
    }

    /** JSON 通道 MockMvc 载体。 */
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

    /** 上传通道 MockMvc 载体。 */
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

    /** 回显载荷。 */
    public static class EdgePayload {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /** 被测端点：echo（JSON）/ upload（multipart）/ 两个业务失败端点。 */
    @RestController
    static class EdgeController {

        @JSecureApi(transType = TRANS_TYPE)
        @PostMapping("/secure/echo")
        public JSecureResponse<EdgePayload> echo(@RequestBody @Valid JSecureRequest<EdgePayload> request) {
            EdgePayload res = new EdgePayload();
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

        @JSecureApi(transType = TRANS_TYPE)
        @PostMapping("/secure/biz-fail")
        public JSecureResponse<EdgePayload> bizFail(@RequestBody @Valid JSecureRequest<EdgePayload> request) {
            return JSecureResponse.fail("业务处理失败：账户余额不足");
        }

        @JSecureApi(transType = TRANS_TYPE)
        @PostMapping("/secure/upload-biz-fail")
        public JSecureResponse<String> uploadBizFail(
                @RequestPart("meta") @Valid JSecureRequest<JSecureUploadDto<Map<String, Object>, JSecureUploadFileDto>> meta,
                @RequestPart(value = "files", required = false) List<MultipartFile> files) {
            return JSecureResponse.fail("业务处理失败：账户余额不足");
        }
    }
}
