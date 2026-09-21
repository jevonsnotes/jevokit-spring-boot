package io.github.jevonsnotes.jevokit.api;

import jakarta.validation.Valid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import io.github.jevonsnotes.jevokit.api.annotation.JSecureApi;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiConfiguration;
import io.github.jevonsnotes.jevokit.api.consts.ApiHSignAlg;
import io.github.jevonsnotes.jevokit.api.dto.JSRequestExecutor;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JSecureApi 安全管线负路径切片测试（@WebMvcTest，不起完整应用上下文）。
 *
 * <p>验证四类拒绝路径 + 一条正向对照（正向通过证明负路径拒绝不是因为管线"一票否决一切"）：
 * <ol>
 *   <li>请求头签名被篡改 → code 999 "请求头验签失败"；</li>
 *   <li>同一 nonce 重放 → code 999 "重复请求或已失效"（10 分钟 TimedCache）；</li>
 *   <li>时间戳过旧（&gt;10 分钟）→ 同一防重放防线拒绝；</li>
 *   <li>access token 错误/缺失 → 拦截器 HTTP 403（路径级访问门，与签名管线独立）。</li>
 * </ol>
 *
 * <p>客户端侧请求由 {@link JSecureApiUtil#getExecutor} 真实构造（MD5 头签名
 * = timestamp + secretKey + nonce，SM4 体加密 + SM2 体签名），负路径仅在传输前
 * 定点篡改对应头，其余要素保持合规——失败原因可归因到被篡改的那一项。
 *
 * <p>切片装配注意：@WebMvcTest 不加载库自动装配，需显式 @Import
 * {@link JevokitApiConfiguration}；其 properties bean 带 @RefreshScope，
 * 需同时导入 spring-cloud {@link RefreshAutoConfiguration} 注册 refresh 作用域。
 */
@WebMvcTest
@Import({JevokitApiConfiguration.class, RefreshAutoConfiguration.class, SecureApiNegativePathTest.EchoController.class})
@TestPropertySource(properties = {
        // ---- 访问令牌门（拦截器路径级）----
        "jevokit.api.access.header=X-Access-Token",
        "jevokit.api.access.tokens[0].path=/token-protected/**",
        "jevokit.api.access.tokens[0].token=valid-token",
        // ---- 签名管线客户端（verify/prevent-replay 全开，负路径才可达）----
        "jevokit.api.client[0].id=neg-test-client",
        "jevokit.api.client[0].secret-key=0123456789abcdef",
        // 演示 SM2 密钥对（与 sample application.yml 同源，仅用于测试）
        "jevokit.api.client[0].private-key=MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk5B6bcSXNwvtfd6nufsDHEPOu8N2hHKJMBW5blZP2eSgCgYIKoEcz1UBgi2hRANCAASxO58/zvEseRlMZtRlojzn7m4jAaZPTDSOalPCSk3nnGW0GmEu2ZhfWDNH+Nq8wA6BLodb2gwI9NF+F1Mp3p9R",
        "jevokit.api.client[0].public-key=MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEsTufP87xLHkZTGbUZaI85+5uIwGmT0w0jmpTwkpN55xltBphLtmYX1gzR/javMAOgS6HW9oMCPTRfhdTKd6fUQ==",
        "jevokit.api.client[0].verify=true",
        "jevokit.api.client[0].prevent-replay=true",
        "jevokit.api.client[0].decrypt=true",
        "jevokit.api.client[0].encrypt=true",
})
@DisplayName("JSecureApi 负路径切片")
class SecureApiNegativePathTest {

    private static final String CLIENT_ID = "neg-test-client";
    private static final String SECRET = "0123456789abcdef";
    private static final String TRANS_TYPE = "TN-NEG-TEST";

    @Autowired
    MockMvc mockMvc;

    // ------------------------------------------------------------------
    // 正向对照：全部校验（头签名/防重放/解密/体签名/响应验签）通过
    // ------------------------------------------------------------------

    @Test
    @DisplayName("正向对照：合规请求走通全管线，code=0 且响应数据往返还原")
    void validRequestPassesWholePipeline() {
        JSecureResponse<EchoPayload> response = prepared("happy").execute(this::postViaMockMvc);
        assertThat(response.getCode()).as("合规请求应返回 code=0").isEqualTo("0");
        assertThat(response.getTarget()).as("响应应解密还原业务对象").isNotNull();
        assertThat(response.getTarget().getName()).isEqualTo("happy");
    }

    // ------------------------------------------------------------------
    // 负路径 1：请求头签名篡改
    // ------------------------------------------------------------------

    @Test
    @DisplayName("请求头签名被篡改：code=999，消息含「请求头验签失败」")
    void tamperedHeaderSignatureRejected() {
        JSRequestExecutor<EchoPayload, EchoPayload> executor = prepared("sig-tamper");
        executor.getHeaders().put("x-api-signature", "deadbeef");

        JSecureResponse<EchoPayload> response = executor.execute(this::postViaMockMvc);
        assertThat(response.getCode()).as("篡改头签名应被拒绝").isEqualTo("999");
        assertThat(response.getMessage()).contains("请求头验签失败");
    }

    // ------------------------------------------------------------------
    // 负路径 2：同一 nonce 重放
    // ------------------------------------------------------------------

    @Test
    @DisplayName("同一 nonce 二次请求：首次 code=0，重放 code=999「重复请求或已失效」")
    void replayedNonceRejected() {
        JSRequestExecutor<EchoPayload, EchoPayload> executor = prepared("replay");

        JSecureResponse<EchoPayload> first = executor.execute(this::postViaMockMvc);
        assertThat(first.getCode()).as("首次请求应成功（对照）").isEqualTo("0");

        JSecureResponse<EchoPayload> replayed = executor.execute(this::postViaMockMvc);
        assertThat(replayed.getCode()).as("同 nonce 二次请求应被防重放拒绝").isEqualTo("999");
        assertThat(replayed.getMessage()).contains("重复请求或已失效");
    }

    // ------------------------------------------------------------------
    // 负路径 3：时间戳过旧（>10 分钟防重放窗口）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("时间戳早于 10 分钟窗口：code=999「重复请求或已失效」（头签名对旧时间戳仍合规）")
    void staleTimestampRejected() {
        JSRequestExecutor<EchoPayload, EchoPayload> executor = prepared("stale-ts");
        // 只动时间戳：用旧时间戳重算合规头签名（timestamp+secret+nonce），
        // 确保拒绝可归因于时间戳过期而非签名不匹配
        String staleTimestamp = String.valueOf(System.currentTimeMillis() / 1000 - 700);
        String nonce = executor.getHeaders().get("x-api-nonce");
        String staleSignature = JSecureApiUtil.headerSignature(
                ApiHSignAlg.MD5, staleTimestamp, nonce, SECRET);
        executor.getHeaders().put("x-api-timestamp", staleTimestamp);
        executor.getHeaders().put("x-api-signature", staleSignature);

        JSecureResponse<EchoPayload> response = executor.execute(this::postViaMockMvc);
        assertThat(response.getCode()).as("过期时间戳应被防重放窗口拒绝").isEqualTo("999");
        assertThat(response.getMessage()).contains("重复请求或已失效");
    }

    // ------------------------------------------------------------------
    // 负路径 4：访问令牌门（拦截器，独立于签名管线）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("访问令牌错误/缺失：HTTP 403；正确令牌：200（路径级访问门）")
    void invalidAccessTokenForbidden() throws Exception {
        // 错误令牌 → 403
        mockMvc.perform(MockMvcRequestBuilders.get("/token-protected/echo")
                        .servletPath("/token-protected/echo")
                        .header("X-Access-Token", "wrong-token"))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
        // 缺失令牌 → 403
        mockMvc.perform(MockMvcRequestBuilders.get("/token-protected/echo")
                        .servletPath("/token-protected/echo"))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
        // 正确令牌 → 200
        mockMvc.perform(MockMvcRequestBuilders.get("/token-protected/echo")
                        .servletPath("/token-protected/echo")
                        .header("X-Access-Token", "valid-token"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string("ok"));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 造一个已 prepare 的真实执行器（合规头签名 + SM4 加密体 + SM2 体签名） */
    private JSRequestExecutor<EchoPayload, EchoPayload> prepared(String name) {
        EchoPayload payload = new EchoPayload();
        payload.setName(name);
        return JSecureApiUtil.getExecutor(CLIENT_ID, "/secure/echo", payload, EchoPayload.class)
                .transNo(UUID.randomUUID().toString())
                .transType(TRANS_TYPE)
                .prepare();
    }

    /** MockMvc 传输层（IRequest 回调）：应用全部请求头并断言 HTTP 200 */
    private String postViaMockMvc(String url, String json, Map<String, String> headers) {
        try {
            MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.post(url)
                    .content(json)
                    .contentType(MediaType.APPLICATION_JSON);
            headers.forEach(requestBuilder::header);
            return mockMvc.perform(requestBuilder)
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 回显载荷（Jackson/fastjson2 双侧序列化：需默认构造器 + 读写器） */
    public static class EchoPayload {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /** 切片内被测端点：/secure/echo 走安全管线；/token-protected/echo 走令牌门 */
    @RestController
    static class EchoController {

        @JSecureApi(transType = TRANS_TYPE)
        @PostMapping("/secure/echo")
        public JSecureResponse<EchoPayload> echo(@RequestBody @Valid JSecureRequest<EchoPayload> request) {
            EchoPayload res = new EchoPayload();
            res.setName(request.getTarget().getName());
            return JSecureResponse.ok(res);
        }

        @GetMapping("/token-protected/echo")
        public String tokenEcho() {
            return "ok";
        }
    }
}
