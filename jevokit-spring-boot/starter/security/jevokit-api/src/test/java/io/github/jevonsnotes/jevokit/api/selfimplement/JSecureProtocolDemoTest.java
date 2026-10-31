package io.github.jevonsnotes.jevokit.api.selfimplement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import io.github.jevonsnotes.jevokit.api.annotation.JSecureApi;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiConfiguration;
import io.github.jevonsnotes.jevokit.api.consts.ApiHSignAlg;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDto;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;
import io.github.jevonsnotes.jevokit.core.util.SecurityUtil;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JSecureProtocolDemo}（不依赖 jevokit 的自实现参考）验收测试：
 * <ol>
 *   <li>真实容器往返（RANDOM_PORT 嵌入式 Tomcat）：参考实现经真实 HTTP 线路对 JSecure 服务端走通
 *       JSON 通道与上传通道全协议链（头签名/防重放/解密/验签/上传校验/响应解密验签）；</li>
 *   <li>字节级互验：参考实现的 MAC/SM2/SM4 输出与 jevokit（hutool）实现逐字节一致
 *       （双向：BC 签 hutool 验、hutool 签 BC 验；BC 加密 hutool 解密、反之亦然）；</li>
 *   <li>负路径：密钥配错时服务端的报错形态（对齐 README"常见坑"排查表）。</li>
 * </ol>
 * 该测试同时是 README"不依赖 jevokit 的自实现对接"章节的长期回归：将来协议口径调整
 * （IV 方式/签名算法/MAC 拼接/信封字段/multipart 形态）导致参考实现失效时，此处先红。
 *
 * <p>装配说明：与 {@code JSecureClientDefaultCarrierTest} 同源的最小服务端
 * （web 自动装配 + JevokitApiConfiguration + 被测端点），不开 jasypt 等其他 starter；
 * 防重放开（prevent-replay=true）验证参考实现的 nonce/timestamp 口径满足防重放。
 */
@SpringBootTest(classes = JSecureProtocolDemoTest.MinimalServerApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        // jevokit.crypto 启动检查（JevokitCoreConfiguration）：演示密钥与 sample application.yml 同源
        "jevokit.crypto.rsa-public-key=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtaljp48OS0mtwPdhmtbP2zBwsC/oolDx2wmYMrs8shSsyVgd1lempOdm12wSP4e/uKsptbZEuX4q950Tg7x+fyhTtzyyavr4Q4ET2/XisTO+EAp/y59SWy7bjDAJU2wB/DZZZ3L7sbiSCK8383vM/EhgczYz+kX4okjqymR4st3TY3OLZ5y826H/R7emvVNy8MjrvZHREcHzoXnTKWvfcpF10CcbJndIh0tiTtD3tdH0v0kOSmN/qhVBcrL5TOlHLqq1V6aSW9xI1LBNouGlw9pFB4RsOQoJsOH1sygFGy8vCLxvwYNroQZNj//VQhTSVQNg1+Ss1YW5TwrUnL+/EQIDAQAB",
        "jevokit.crypto.rsa-private-key=MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC1qWOnjw5LSa3A92Ga1s/bMHCwL+iiUPHbCZgyuzyyFKzJWB3WV6ak52bXbBI/h7+4qym1tkS5fir3nRODvH5/KFO3PLJq+vhDgRPb9eKxM74QCn/Ln1JbLtuMMAlTbAH8NllncvuxuJIIrzfze8z8SGBzNjP6RfiiSOrKZHiy3dNjc4tnnLzbof9Ht6a9U3LwyOu9kdERwfOhedMpa99ykXXQJxsmd0iHS2JO0Pe10fS/SQ5KY3+qVUFysvlM6UcuqrVXppJb3EjUsE2i4aXD2kUHhGw5Cgmw4fWzKAUbLy8IvG/Bg2uhBk2P/9VCFNJVA2DX5KzVhblPCtScv78RAgMBAAECggEANKby5lbVSscBIGPTG2DVf+8rSjWolJsnblf6kAGq1uadKgJrK3veJf+X1CpTo6xBylF51lwM0UKbS4dwZ4dTlqFmmj5ybAVvvYkE64T5/mhD1zxjy+3SnfEX2yr1GQO+E47hHNW708fuM6wtaFnikOCda+cy64Aq0cZHi4+uYCKxGL9PtYzeNGwHygxJV3XaNBD48IzHUnIstWYEWFmqSPbknRxXhGJnVoEw9JygRngaKoRchairX/FJcwgxcaWwUQMZaLALW5xsB9OIUvsOA5+PzTIy+hjgiaIiIvUV2yF16dkkzz9x3fdHYGQer+ezNbXKLd2/lSmku/R7V3QKBgQDbW5949LAdXLwNqKg244wCafVIUyVwNT9yNWkuGZ5B69mfFmo+0Rx7CCPbbbyMF6hcWMwEO8qQPqZj7w+EJ4T5TYApH5LM4s5NZVeXUMZn+0DZgTsCzorsibv0VNbGVs6jCFrrBv1U/bUhy2Qyt8fUCgb07yYYcEVuk5sqqpjQwKBgQDUAcRV6xTO10edNyxvktk7hn6BtXafbfiNhC9xCjsTrIBUhsF1dG85mVvsvgXLDmHbGvgVvVsGI/Ka6X99mv5OOtlmF2YkFlWpUci9mZCcnbG7jKLDF/X6tRHuVtZdGMucoCqAnLuDw+5XvMnKvjgYBLKgT02ngOs+HKV+tGtGwKBgHf5EKuTCI/GmIPL7tS2d2kKksDP2PYW6W2JkNFJ6aicWhYD6DAbJryToyjsR+dHOSno1wyptwxVePvlk3tOk2bGQlbvoLN4qXjMubk2wPBO686cCaUgLtyfP3b6v80SxTtlenTViTUQizuKA33ht07lBErT755YOMatcx8z6KUJAoGBAJYVhiN432sqdhTzKO73m3eLV+WFTvCvDU1EDwf4ZSwc7eEHef/ghbFfvXGc1gq5kSRwudHAXc+fd7PKD2HBYGC3Td6XHToMhtVNOsf7adjnj9xV5A5GERaoxVPuBjtOqX/Vq1TqaMQ9aKPQxyzlntDvDwsnzPj0EqDdoWrC/Gt0b/AoGAfhEIqXzhubeOtsYHOswkSI20PkhXSlNY9KARUiN5xOyqv7Z8IM2TXzSvPQFiMoPVJqn4/2sWGQh/UC23rXbhCsfW7NGloJhlKM76BptqFsn3EV6w4PN2Au2UtVa17qhM8g2TbOZLBGxHTxecWTwuHu+w70bsgxOwvzTxkYcYk+g=",
        "jevokit.crypto.aes-key=DemoAesKey123456",
        "jevokit.crypto.sm4-key=DemoSm4Key123456",
        "jevokit.api.client[0].id=selfimpl-e2e-client",
        "jevokit.api.client[0].secret-key=0123456789abcdef",
        // 演示 SM2 密钥对（与 sample application.yml 同源，仅用于测试）
        "jevokit.api.client[0].private-key=MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk5B6bcSXNwvtfd6nufsDHEPOu8N2hHKJMBW5blZP2eSgCgYIKoEcz1UBgi2hRANCAASxO58/zvEseRlMZtRlojzn7m4jAaZPTDSOalPCSk3nnGW0GmEu2ZhfWDNH+Nq8wA6BLodb2gwI9NF+F1Mp3p9R",
        "jevokit.api.client[0].public-key=MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEsTufP87xLHkZTGbUZaI85+5uIwGmT0w0jmpTwkpN55xltBphLtmYX1gzR/javMAOgS6HW9oMCPTRfhdTKd6fUQ==",
        "jevokit.api.client[0].verify=true",
        "jevokit.api.client[0].prevent-replay=true",
        "jevokit.api.client[0].decrypt=true",
        "jevokit.api.client[0].encrypt=true",
})
@DisplayName("不依赖 jevokit 的自实现参考（JSecureProtocolDemo）：真实容器往返 + 字节级互验")
class JSecureProtocolDemoTest {

    private static final String CLIENT_ID = "selfimpl-e2e-client";
    private static final String TRANS_TYPE = "TN-SELFIMPL-E2E";
    private static final String SECRET_KEY = "0123456789abcdef";
    // 演示 SM2 密钥对（与服务端成对登记，sample 同源）
    private static final String PRIVATE_KEY =
            "MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk5B6bcSXNwvtfd6nufsDHEPOu8N2hHKJMBW5blZP2eSgCgYIKoEcz1UBgi2hRANCAASxO58/zvEseRlMZtRlojzn7m4jAaZPTDSOalPCSk3nnGW0GmEu2ZhfWDNH+Nq8wA6BLodb2gwI9NF+F1Mp3p9R";
    private static final String PUBLIC_KEY =
            "MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEsTufP87xLHkZTGbUZaI85+5uIwGmT0w0jmpTwkpN55xltBphLtmYX1gzR/javMAOgS6HW9oMCPTRfhdTKd6fUQ==";
    // 另一对演示密钥的私钥（sample client-demo-2）：用于"私钥不配对"负路径
    private static final String OTHER_PRIVATE_KEY =
            "MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgTZMk6BwQwTqmU+pg/ObaUqMuYtB0wb7jN1aCqjhKB8CgCgYIKoEcz1UBgi2hRANCAASsN3z5bjfH04PH//4kVKrPRBo1aM0Pt8egiQQltEh6BmcwFt9v4yOq5HjX9UHA84MBNgWfAOgxcffcM9+NWGBC";

    @LocalServerPort
    int port;


    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    /** 参考实现实例（默认头前缀 x-api、算法 md5，与服务端终端缺省一致）。 */
    private JSecureProtocolDemo demo() {
        return new JSecureProtocolDemo(CLIENT_ID, SECRET_KEY, SECRET_KEY, PRIVATE_KEY, PUBLIC_KEY);
    }

    // ===================== 真实容器往返（两通道全协议链） =====================

    @Test
    @DisplayName("JSON 通道：自实现报文过真实容器全链（头签名/防重放/解密/验签），响应解密验签还原")
    void jsonChannelRoundTripsRealContainer() {
        String view = demo().postJson(baseUrl() + "/secure/echo",
                "{\"name\":\"self-implement\",\"中文\":\"内容\"}",
                "t-selfimpl-json-001", TRANS_TYPE);

        assertThat(view).as("响应应为已解密验签的业务明文").contains("self-implement").contains("内容");
    }

    @Test
    @DisplayName("上传通道：手工 multipart（meta 信封 + files part）被真实容器按协议受理")
    void uploadChannelRoundTripsRealContainer() {
        String view = demo().uploadFiles(baseUrl() + "/secure/upload",
                "{\"orderNo\":\"SO-SELFIMPL-001\"}",
                "t-selfimpl-upload-001", TRANS_TYPE,
                List.of(
                        new JSecureProtocolDemo.UploadFile("inv-1", "invoice", "发票.pdf", "pdf",
                                "invoice-bytes-自实现".getBytes(StandardCharsets.UTF_8)),
                        new JSecureProtocolDemo.UploadFile("id-1", "id_card", "身份证.jpg", "jpg",
                                "id-card-bytes".getBytes(StandardCharsets.UTF_8))));

        // 服务端返回 String target → 响应明文为 JSON 字符串字面量（带引号）
        assertThat(view).as("声明 2 个文件、发送 2 个 part，服务端应受理 2/2")
                .isEqualTo("\"accepted:2/2\"");
    }

    // ===================== 字节级互验（与 jevokit 的 hutool 实现双向一致） =====================

    @Test
    @DisplayName("MAC 配方一致：md5/sha256 摘要与 JSecureApiUtil.headerSignature 逐字节相同")
    void headerSignMatchesJevokitFormula() {
        JSecureProtocolDemo md5Demo =
                new JSecureProtocolDemo(CLIENT_ID, SECRET_KEY, SECRET_KEY, PRIVATE_KEY, PUBLIC_KEY, "x-api", "md5");
        JSecureProtocolDemo sha256Demo =
                new JSecureProtocolDemo(CLIENT_ID, SECRET_KEY, SECRET_KEY, PRIVATE_KEY, PUBLIC_KEY, "x-api", "sha256");
        String timestamp = "1770000000";
        String nonce = "6f2b3c1d-8e4a-4f2b-9c1d-000000000000";

        assertThat(md5Demo.headerSign(timestamp, nonce))
                .isEqualTo(JSecureApiUtil.headerSignature(ApiHSignAlg.MD5, timestamp, nonce, SECRET_KEY));
        assertThat(sha256Demo.headerSign(timestamp, nonce))
                .isEqualTo(JSecureApiUtil.headerSignature(ApiHSignAlg.SHA256, timestamp, nonce, SECRET_KEY));
        assertThat(sha256Demo.headerSign(timestamp, nonce))
                .as("摘要应为 hex 小写")
                .matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("SM2 签名互通：BC 签 hutool 验（服务端验签路径） + hutool 签 BC 验（响应验签路径），含中文")
    void sm2SignInteropWithJevokitImpl() {
        String ascii = "plain-ascii-content";
        String chinese = "中文签名内容-自实现互验";
        JSecureProtocolDemo demo = demo();

        // 参考实现签 → jevokit（hutool）验：即服务端 verifyBody 的路径
        assertThat(SecurityUtil.sm2Base64Verify(ascii, demo.sm2Sign(ascii), PUBLIC_KEY)).isTrue();
        assertThat(SecurityUtil.sm2Base64Verify(chinese, demo.sm2Sign(chinese), PUBLIC_KEY)).isTrue();
        // jevokit（hutool）签 → 参考实现验：即客户端验响应签名的路径
        assertThat(demo.sm2Verify(ascii, SecurityUtil.sm2Base64Sign(ascii, PRIVATE_KEY))).isTrue();
        assertThat(demo.sm2Verify(chinese, SecurityUtil.sm2Base64Sign(chinese, PRIVATE_KEY))).isTrue();
        // 篡改一字节验签必须失败（防"恒真"实现）
        assertThat(demo.sm2Verify(chinese + "x",
                SecurityUtil.sm2Base64Sign(chinese, PRIVATE_KEY))).isFalse();
    }

    @Test
    @DisplayName("SM4 互通：BC 加密 hutool 解密 + hutool 加密 BC 解密，中文内容逐字节还原")
    void sm4InteropWithJevokitImpl() {
        String content = "{\"name\":\"中文-内容\",\"amount\":88.5}";
        JSecureProtocolDemo demo = demo();

        // 参考实现加密 → 服务端 sm4cbcDecrypt 路径
        assertThat(SecurityUtil.sm4cbcDecrypt(demo.sm4CbcEncrypt(content), SECRET_KEY)).isEqualTo(content);
        // 服务端加密（响应 data）→ 参考实现解密路径
        assertThat(demo.sm4CbcDecrypt(SecurityUtil.sm4cbcEncrypt(content, SECRET_KEY))).isEqualTo(content);
    }

    @Test
    @DisplayName("信封序列化形态：字段名精确，transType 为 null 时显式写 null（fastjson2 WriteMapNullValue）")
    void envelopeShapeMatchesProtocolSpec() {
        // transType 为 null 时显式写 null（WriteMapNullValue），与参考实现既定 wire 形态一致；
        // 服务端对"显式 null"与 jevokit 客户端 fastjson2 默认的"省略字段"两种形态均受理（容器用例回归兜底）
        assertThat(JSecureProtocolDemo.buildEnvelope("t-1", null, "sig", "data"))
                .isEqualTo("{\"transNo\":\"t-1\",\"transType\":null,\"signature\":\"sig\",\"data\":\"data\"}");
        assertThat(JSecureProtocolDemo.buildEnvelope("t-2", "QUERY", "s", "d"))
                .isEqualTo("{\"transNo\":\"t-2\",\"transType\":\"QUERY\",\"signature\":\"s\",\"data\":\"d\"}");
    }

    // ===================== 负路径（密钥配错的服务端报错形态，对齐 README 常见坑排查表） =====================

    @Test
    @DisplayName("头签名密钥配错：服务端报 请求头验签失败（code=999）")
    void wrongHeaderSecretRejected() {
        JSecureProtocolDemo wrongMac = new JSecureProtocolDemo(
                CLIENT_ID, "ffffffffffffffff", SECRET_KEY, PRIVATE_KEY, PUBLIC_KEY);
        assertThatThrownBy(() -> wrongMac.postJson(baseUrl() + "/secure/echo",
                "{\"name\":\"x\"}", "t-selfimpl-neg-001", TRANS_TYPE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("请求头验签失败");
    }

    @Test
    @DisplayName("SM4 密钥配错：服务端报 接口解密失败（code=999）")
    void wrongSm4KeyRejected() {
        JSecureProtocolDemo wrongSm4 = new JSecureProtocolDemo(
                CLIENT_ID, SECRET_KEY, "ffffffffffffffff", PRIVATE_KEY, PUBLIC_KEY);
        assertThatThrownBy(() -> wrongSm4.postJson(baseUrl() + "/secure/echo",
                "{\"name\":\"x\"}", "t-selfimpl-neg-002", TRANS_TYPE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("接口解密失败");
    }

    @Test
    @DisplayName("SM2 私钥不配对：服务端报 请求体验签失败（code=999）")
    void wrongPrivateKeyRejected() {
        JSecureProtocolDemo wrongSign = new JSecureProtocolDemo(
                CLIENT_ID, SECRET_KEY, SECRET_KEY, OTHER_PRIVATE_KEY, PUBLIC_KEY);
        assertThatThrownBy(() -> wrongSign.postJson(baseUrl() + "/secure/echo",
                "{\"name\":\"x\"}", "t-selfimpl-neg-003", TRANS_TYPE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("请求体验签失败");
    }

    // ===================== 最小服务端装配 =====================

    /** 最小服务端装配：web 自动装配（嵌入式 Tomcat + MVC）+ 协议配置 + 被测端点。 */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({RefreshAutoConfiguration.class, JevokitApiConfiguration.class, DemoController.class})
    static class MinimalServerApp {
    }

    /** 被测端点：JSON 通道 /secure/echo + 上传通道 /secure/upload。 */
    @RestController
    static class DemoController {

        @JSecureApi(transType = TRANS_TYPE)
        @PostMapping("/secure/echo")
        public JSecureResponse<Map<String, Object>> echo(
                @RequestBody @jakarta.validation.Valid JSecureRequest<Map<String, Object>> request) {
            return JSecureResponse.ok(request.getTarget());
        }

        @JSecureApi(transType = TRANS_TYPE)
        @PostMapping("/secure/upload")
        public JSecureResponse<String> upload(
                @RequestPart("meta") @jakarta.validation.Valid
                JSecureRequest<JSecureUploadDto<Map<String, Object>, JSecureUploadFileDto>> meta,
                @RequestPart(value = "files", required = false) List<MultipartFile> files) {
            int declared = meta.getTarget().getFiles() == null ? 0 : meta.getTarget().getFiles().size();
            int bound = files == null ? 0 : files.size();
            return JSecureResponse.ok("accepted:" + declared + "/" + bound);
        }
    }
}
