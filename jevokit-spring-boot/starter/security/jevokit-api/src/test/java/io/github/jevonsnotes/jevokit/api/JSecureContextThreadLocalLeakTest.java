package io.github.jevonsnotes.jevokit.api;

import com.alibaba.fastjson2.JSON;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
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
import io.github.jevonsnotes.jevokit.api.context.JSecureContext;
import io.github.jevonsnotes.jevokit.api.dto.JSRequestExecutor;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * JSecureContext ThreadLocal 泄漏实证测试（@WebMvcTest 切片，不起完整应用上下文）。
 *
 * <p>被验证的三个怀疑（生产代码静态阅读得出，本类用行为实验定论）：
 * <ol>
 *   <li><b>A 成功请求后 ThreadLocal 残留</b>：EncryptResponseAdviceAdapter#beforeBodyWrite 成功分支
 *       直接 return resp 不清理（remove 只在几乎不可达的 else 分支与异常 handler 的 finally），
 *       JevokitApiAccessInterceptor#afterCompletion 为空实现 → 请求结束后
 *       JS_REQUEST / CLIENT / LOG_PREFIX 三个 ThreadLocal 均残留；</li>
 *   <li><b>B 线程复用后 CLIENT 缓存命中上个请求的终端</b>：JSecureContext#getCurrentClient 对
 *       CLIENT_THREAD_LOCAL 命中即返回、不按当前请求头 client-id 重新解析 → 同线程复用时
 *       终端 B 的请求被用终端 A 的密钥验签/解密；</li>
 *   <li><b>C 残留 jsr 使非 JSecure 端点的异常被吞</b>：EncryptResponseAdviceAdapter#handler 的
 *       {@code jsr == null} 守卫因残留失效 → 普通（非 JSecure）端点抛出的 RuntimeException
 *       被转成 FAIL 协议响应，且 transNo 沿用上一个 JSecure 请求的值。</li>
 * </ol>
 *
 * <p>线程复用模拟：MockMvc 的 perform() 在调用线程内同步执行整个请求处理
 * （等价于容器线程池中同线程先后处理两次请求），因此同一测试方法内连续两次 perform
 * 即天然复现"线程池同线程复用"场景，无需真实线程池。
 *
 * <p>终端构造：client A 使用与 sample application.yml 同源的演示 SM2 密钥对；client B 的
 * SM2 密钥对为离线独立生成（格式与 A 相同，仅用于测试）。两个终端的
 * secretKey / secretKeySm4 / SM2 密钥对互不相同，各自请求按自身密钥正确加签加密。
 *
 * <p>断言方向：本类全部断言"<b>正确行为</b>"（无泄漏时应当成立）——断言失败即泄漏实锤，
 * 失败消息中会带出实际值（如 code=999 / message=请求头验签失败 / 残留对象 toString）作为证据。
 */
@WebMvcTest
@Import({JevokitApiConfiguration.class, RefreshAutoConfiguration.class,
        JSecureContextThreadLocalLeakTest.LeakProbeController.class})
@TestPropertySource(properties = {
        // ---- 终端 A：演示 SM2 密钥对（与 sample application.yml 同源，仅用于测试）----
        "spkit.api.client[0].id=leak-client-a",
        "spkit.api.client[0].secret-key=aaaabbbbccccdddd",
        "spkit.api.client[0].secret-key-sm4=aaaabbbbccccdddd",
        "spkit.api.client[0].private-key=MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk5B6bcSXNwvtfd6nufsDHEPOu8N2hHKJMBW5blZP2eSgCgYIKoEcz1UBgi2hRANCAASxO58/zvEseRlMZtRlojzn7m4jAaZPTDSOalPCSk3nnGW0GmEu2ZhfWDNH+Nq8wA6BLodb2gwI9NF+F1Mp3p9R",
        "spkit.api.client[0].public-key=MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEsTufP87xLHkZTGbUZaI85+5uIwGmT0w0jmpTwkpN55xltBphLtmYX1gzR/javMAOgS6HW9oMCPTRfhdTKd6fUQ==",
        "spkit.api.client[0].verify=true",
        "spkit.api.client[0].decrypt=true",
        "spkit.api.client[0].encrypt=true",
        "spkit.api.client[0].prevent-replay=false",
        // ---- 终端 B：secretKey/SM4/SM2 密钥对均与 A 不同（SM2 为离线独立生成的测试密钥对）----
        "spkit.api.client[1].id=leak-client-b",
        "spkit.api.client[1].secret-key=ddddccccbbbbaaaa",
        "spkit.api.client[1].secret-key-sm4=ddddccccbbbbaaaa",
        "spkit.api.client[1].private-key=MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgRXeF941AVfKIFM4Ua4cyCsKCTgtUdYRjLr2yftmA5aqgCgYIKoEcz1UBgi2hRANCAASRu0AJMFKxU/Cs+Zb9dgSV3E+uaLHd+OpmzqYCB4Qgd4vc/guwAuCsp0rYfgND3mrCRpVPsJrc38pR8nnube6a",
        "spkit.api.client[1].public-key=MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEkbtACTBSsVPwrPmW/XYEldxPrmix3fjqZs6mAgeEIHeL3P4LsALgrKdK2H4DQ95qwkaVT7Ca3N/KUfJ57m3umg==",
        "spkit.api.client[1].verify=true",
        "spkit.api.client[1].decrypt=true",
        "spkit.api.client[1].encrypt=true",
        "spkit.api.client[1].prevent-replay=false",
})
@DisplayName("JSecureContext ThreadLocal 泄漏实证")
class JSecureContextThreadLocalLeakTest {

    private static final String CLIENT_A_ID = "leak-client-a";
    private static final String CLIENT_B_ID = "leak-client-b";
    private static final String TRANS_TYPE = "TN-LEAK-TEST";
    private static final String URL_SECURE_ECHO = "/secure/echo";
    private static final String URL_PLAIN_BOOM = "/plain/boom";
    private static final String BOOM_MARKER = "plain-boom-marker";

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void cleanThreadLocal() {
        // 每个用例从干净状态出发，保证用例间互不污染（生产代码缺的清理，测试侧补齐以保证可重复）
        JSecureContext.remove();
    }

    // ------------------------------------------------------------------
    // 用例 1（怀疑 A）：成功请求后 ThreadLocal 残留
    // ------------------------------------------------------------------

    @Test
    @DisplayName("怀疑A：成功的 JSecure 请求结束后，三个 ThreadLocal 应为空（非空即泄漏实锤）")
    void successRequestLeavesThreadLocalResidue() {
        JSecureResponse<EchoPayload> resp = prepared(CLIENT_A_ID, "leak-check")
                .transNo("trans-residue-" + UUID.randomUUID())
                .execute(this::postViaMockMvc);
        // 前置条件：请求本身必须成功，残留才可归因于"成功路径不清理"
        assertThat(resp.getCode()).as("前置条件：终端A的合规请求应成功（code=0）").isEqualTo("0");

        // perform() 已在当前线程同步返回，请求生命周期已结束，此时线程上下文应为空。
        // 软断言一次性呈现三个 ThreadLocal 的残留证据（不因首个断言失败而中断）
        assertSoftly(softly -> {
            softly.assertThat(JSecureContext.getRequest())
                    .as("怀疑A：JS_REQUEST_THREAD_LOCAL 应为 null，实际残留=%s", JSecureContext.getRequest())
                    .isNull();
            softly.assertThat(JSecureContext.CLIENT_THREAD_LOCAL.get())
                    .as("怀疑A：CLIENT_THREAD_LOCAL 应为 null，实际残留终端=%s",
                            JSecureContext.CLIENT_THREAD_LOCAL.get() == null
                                    ? "-" : JSecureContext.CLIENT_THREAD_LOCAL.get().getId())
                    .isNull();
            softly.assertThat(JSecureContext.LOG_PREFIX.get())
                    .as("怀疑A：LOG_PREFIX 应为 null，实际残留=%s", JSecureContext.getLogPrefix())
                    .isNull();
        });
    }

    // ------------------------------------------------------------------
    // 用例 2（怀疑 B）：同线程复用后 CLIENT 缓存命中上个终端
    // ------------------------------------------------------------------

    @Test
    @DisplayName("怀疑B：同线程先终端A成功、再发终端B的正确签名请求，B 应以自己的密钥成功（999/验签失败即实锤）")
    void staleClientCacheServesWrongClientOnSameThread() {
        // 对照阶段（排除替代解释）：干净线程上终端 B 单独请求必须成功，证明 B 的密钥配置本身正确
        JSecureResponse<EchoPayload> bAlone = prepared(CLIENT_B_ID, "b-alone")
                .transNo("trans-b-alone-" + UUID.randomUUID())
                .execute(this::postViaMockMvc);
        assertThat(bAlone.getCode())
                .as("对照：干净线程上终端B的请求应成功（证明 B 的密钥/签名组装无误）；"
                        + "实际 code=%s, message=%s", bAlone.getCode(), bAlone.getMessage())
                .isEqualTo("0");

        // 模拟线程池调度到新请求前的干净状态（生产中应由拦截器 afterCompletion 清理）
        JSecureContext.remove();

        // 第 1 步：终端 A 的合规请求，成功（此后 CLIENT_THREAD_LOCAL 残留终端 A，见怀疑A）
        JSecureResponse<EchoPayload> first = prepared(CLIENT_A_ID, "from-a")
                .transNo("trans-a-" + UUID.randomUUID())
                .execute(this::postViaMockMvc);
        assertThat(first.getCode()).as("前置条件：终端A的首次请求应成功（code=0）").isEqualTo("0");
        assertThat(first.getTarget().getServedClientId())
                .as("前置条件：首次请求应由终端A自己的配置处理")
                .isEqualTo(CLIENT_A_ID);

        // 第 2 步：同一线程（MockMvc 同步执行）再发终端 B 的请求，签名/加密全部按 B 自己的密钥正确组装。
        // 若 CLIENT_THREAD_LOCAL 残留终端 A：getCurrentClient 缓存命中返回 A，
        // verifyHeader 用 A 的 secretKey 验 B 的签名 → "请求头验签失败" code=999
        JSecureResponse<EchoPayload> second = prepared(CLIENT_B_ID, "from-b")
                .transNo("trans-b-" + UUID.randomUUID())
                .execute(this::postViaMockMvc);
        assertThat(second.getCode())
                .as("怀疑B实锤判定：终端B的合规请求应以自己的密钥成功（code=0）；"
                        + "实际 code=%s, message=%s, transNo=%s（若为 999/请求头验签失败即实锤：用了终端A的密钥验终端B的签名）",
                        second.getCode(), second.getMessage(), second.getTransNo())
                .isEqualTo("0");
        assertThat(second.getTarget().getServedClientId())
                .as("怀疑B实锤判定：请求应由终端B自己的配置处理；实际 servedClientId=%s（若为 leak-client-a 即实锤：服务端按上个请求的终端处理）",
                        second.getTarget() == null ? "-" : second.getTarget().getServedClientId())
                .isEqualTo(CLIENT_B_ID);
        assertThat(second.getTarget().getName()).as("终端B的响应数据应正常回显").isEqualTo("from-b");
    }

    // ------------------------------------------------------------------
    // 用例 3（怀疑 C）：残留 jsr 吞掉非 JSecure 端点的异常
    // ------------------------------------------------------------------

    @Test
    @DisplayName("怀疑C：成功 JSecure 请求后，同线程非 JSecure 端点的 RuntimeException 应正常传播（被转成协议 FAIL 即实锤）")
    void staleJsrSwallowsNonJSecureException() {
        // 阶段 1（干净对照）：无残留时，/plain/boom 的 RuntimeException 应向上传播（MockMvc 以异常抛出）
        PerformOutcome control = performPlainBoom();
        assertThat(control.error)
                .as("对照阶段（无残留）：非 JSecure 端点的异常应向上传播；"
                        + "若此处非空请先核对对照行为，实际响应体=%s", control.body)
                .isNotNull();
        assertThat(causeChainMessages(control.error))
                .as("对照阶段：传播的异常应携带业务标记（证明未被任何 handler 吞掉转换）")
                .contains(BOOM_MARKER);

        // 阶段 2：同线程发一次成功的 JSecure 请求（其 jsr 残留在本线程，见怀疑A）
        String transNoA = "trans-stale-jsr-" + UUID.randomUUID();
        JSecureResponse<EchoPayload> ok = prepared(CLIENT_A_ID, "before-boom")
                .transNo(transNoA)
                .execute(this::postViaMockMvc);
        assertThat(ok.getCode()).as("前置条件：终端A的合规请求应成功（code=0）").isEqualTo("0");

        // 阶段 3：同线程再打非 JSecure 端点，期望与对照阶段一致：异常传播。
        // 若残留 jsr 使 handler() 的 jsr==null 守卫失效 → 异常被转成 FAIL 协议响应且 transNo=transNoA
        PerformOutcome afterLeak = performPlainBoom();
        if (afterLeak.error == null) {
            // 异常没有传播、返回了响应体：直接给出实锤证据断言（失败即实锤，消息中带全量 body）
            JSecureResponse<?> swallowed = JSON.parseObject(afterLeak.body, JSecureResponse.class);
            assertThat(swallowed.getTransNo())
                    .as("怀疑C实锤判定：异常被转成协议响应且 transNo 沿用了上一个 JSecure 请求（%s）；"
                            + "实际响应 code=%s, message=%s, body=%s",
                            transNoA, swallowed.getCode(), swallowed.getMessage(), afterLeak.body)
                    .isNotEqualTo(transNoA);
        } else {
            assertThat(causeChainMessages(afterLeak.error))
                    .as("怀疑C判定：有残留时异常仍应正常传播（与对照阶段一致）")
                    .contains(BOOM_MARKER);
        }

        // 阶段 4（因果对照）：手动清理残留后，同端点异常应恢复传播——证明阶段 3 的吞异常由残留引起
        JSecureContext.remove();
        PerformOutcome afterClean = performPlainBoom();
        assertThat(afterClean.error)
                .as("因果对照：手动清理残留后异常应恢复传播；实际响应体=%s", afterClean.body)
                .isNotNull();
        assertThat(causeChainMessages(afterClean.error))
                .as("因果对照：恢复传播的异常应携带业务标记")
                .contains(BOOM_MARKER);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 造一个已 prepare 的真实执行器（按指定终端自身密钥组装头签名 + SM4 加密体 + SM2 体签名）。 */
    private JSRequestExecutor<EchoPayload, EchoPayload> prepared(String clientId, String name) {
        EchoPayload payload = new EchoPayload();
        payload.setName(name);
        return JSecureApiUtil.getExecutor(clientId, URL_SECURE_ECHO, payload, EchoPayload.class)
                .transType(TRANS_TYPE)
                .prepare();
    }

    /** MockMvc 传输层（IRequest 回调）：应用全部请求头并断言 HTTP 200（同步执行=同线程复用前提）。 */
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
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 打非 JSecure 的 /plain/boom：捕获是"抛异常"还是"返回响应体"两种结局。 */
    private PerformOutcome performPlainBoom() {
        PerformOutcome outcome = new PerformOutcome();
        try {
            outcome.body = mockMvc.perform(MockMvcRequestBuilders.get(URL_PLAIN_BOOM))
                    .andReturn()
                    .getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            outcome.error = e;
        }
        return outcome;
    }

    /** 汇总异常链上所有 message（ServletException 多层包装时仍可定位业务标记）。 */
    private static String causeChainMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        while (cur != null) {
            sb.append(String.valueOf(cur.getMessage())).append(" <- ");
            cur = cur.getCause();
        }
        return sb.toString();
    }

    /** /plain/boom 一次调用的结局：要么抛异常（error），要么返回响应体（body）。 */
    private static final class PerformOutcome {
        Throwable error;
        String body;
    }

    /** 回显载荷（Jackson/fastjson2 双侧序列化：需默认构造器 + 读写器）。 */
    public static class EchoPayload {
        private String name;
        /** 服务端实际处理本请求的终端识别码（回显 JSecureContext.getCurrentClient 的解析结果）。 */
        private String servedClientId;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getServedClientId() {
            return servedClientId;
        }

        public void setServedClientId(String servedClientId) {
            this.servedClientId = servedClientId;
        }
    }

    /** 切片内被测端点：/secure/echo 走安全管线并回显实际服务的终端；/plain/boom 非 JSecure 且必抛异常。 */
    @RestController
    static class LeakProbeController {

        @JSecureApi(transType = TRANS_TYPE)
        @PostMapping(URL_SECURE_ECHO)
        public JSecureResponse<EchoPayload> echo(@RequestBody @Valid JSecureRequest<EchoPayload> request) {
            EchoPayload res = new EchoPayload();
            res.setName(request.getTarget().getName());
            res.setServedClientId(JSecureContext.getCurrentClientThrowIfInvalid().getId());
            return JSecureResponse.ok(res);
        }

        @GetMapping(URL_PLAIN_BOOM)
        public String boom() {
            throw new RuntimeException(BOOM_MARKER);
        }
    }
}
