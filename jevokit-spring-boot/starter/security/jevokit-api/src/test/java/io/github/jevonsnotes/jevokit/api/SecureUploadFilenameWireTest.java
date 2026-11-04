package io.github.jevonsnotes.jevokit.api;

import com.alibaba.fastjson2.JSON;
import jakarta.validation.Valid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import io.github.jevonsnotes.jevokit.api.annotation.JSecureApi;
import io.github.jevonsnotes.jevokit.api.client.JSecureClient;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiConfiguration;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiProperties;
import io.github.jevonsnotes.jevokit.api.dto.FetchMode;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDetailDto;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;
import io.github.jevonsnotes.jevokit.api.upload.JSecureUploadUtil;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 上传协议 fileId 特殊字符的<b>真实线路</b>边界（RANDOM_PORT 真容器 + 手工构造原始 multipart）：
 * MockMvc 无法验证 multipart 报文序列化/反序列化的 wire 级真话——恶意 filename（引号走私、
 * CRLF 注入）对真实容器解析器的影响。
 *
 * <p>手法：协议头与加密 meta 由<b>手工构造的加密信封</b>组装（恶意 fileId 原样声明——
 * 随 BUG-4/5 收紧后 UploadChannel.add 会在入口 fail-fast 拒绝，本测试模拟的是绕过客户端的
 * 攻击者完全控报文），文件 part 的 Content-Disposition 由本测试逐字节写死（不做任何转义），
 * 经 HttpURLConnection 打到嵌入式 Tomcat，断言服务端不受理且响应可预期；
 * 中文 fileId 与含 / 的 fileId 随白名单收紧改为客户端入口拒绝（断言同步更新）。
 *
 * <p>装配说明：与 JSecureClientDefaultCarrierTest 同源（显式 classes 最小装配，不标
 * SpringBootConfiguration，防切片锚点误吸）。
 */
@SpringBootTest(classes = SecureUploadFilenameWireTest.MinimalWireApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        // jevokit.crypto 启动检查（JevokitCoreConfiguration）：演示密钥与 sample application.yml 同源
        "jevokit.crypto.rsa-public-key=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtaljp48OS0mtwPdhmtbP2zBwsC/oolDx2wmYMrs8shSsyVgd1lempOdm12wSP4e/uKsptbZEuX4q950Tg7x+fyhTtzyyavr4Q4ET2/XisTO+EAp/y59SWy7bjDAJU2wB/DZZZ3L7sbiSCK8383vM/EhgczYz+kX4okjqymR4st3TY3OLZ5y826H/R7emvVNy8MjrvZHREcHzoXnTKWvfcpF10CcbJndIh0tiTtD3tdH0v0kOSmN/qhVBcrL5TOlHLqq1V6aSW9xI1LBNouGlw9pFB4RsOQoJsOH1syZgFGy8vCLXvwYNroQZNj//VQhTSVQNg1+SzVhblPCtUnL+/EQIDAQAB",
        "jevokit.crypto.rsa-private-key=MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC1qWOnjw5LSa3A92Ga1s/bMHCwL+iiUPHbCZgyuzyyFKzJWB3WV6ak52bXbBI/h7+4qym1tkS5fir3nRODvH5/KFO3PLJq+vhDgRPb9eKxM74QCn/Ln1JbLtuMMAlTbAH8NllncvuxuJIIrzfze8z8SGBzNjP6RfiiSOrKZHiy3dNjc4tnLzbof9Ht6a9U3LwyOu9kdERwfOhedMpa99ykXXQJxsmd0iHS2JO0Pe10fS/SQ5KY3+qFUFysvlM6UcuqrVXppJb3EjUsE2i4aXD2kUHhGw5Cgmw4fWzKAUbLy8IvG/Bg2uhBk2P/9VCFNJVA2DX5KzVhblPCtScv78RAgMBAAECggEANKby5lbVSscBIGPTG2DVf+8rSjWolJsnblf6kAGq1uadKgJrK3veJf+X1CpTo6xBylF51lwM0UKbS4dwZ4dTlqFmmj5ybAVvvYkE64T5/mhD1zxjy+3SnfEX2yr1GQO+E47hHNW708fuM6wtaFnikOCda+cy64AqT0cZHi4+uYCKxGL9PtYzeNGwHygxJV3XaNBD48iMzHUnIstWYEWFmqSPbknRxXhGJVoEw9JygRngaKoRchairX/FJcwgxcaWwUQMZaLALW5xsB9OIUvsOA+P+TzTIy+hjgiaIiIvUV2yF16dkkzz3x3fdHYGQer+ezNbXKLd2/lSmku/R7V3KBgQDbW5949LAdXLwNqKg24wCafVIUyVwNT9yNWkuGZ5B69mfBmo+0Rx7CCPbbbyMF6hcWMwEO8oQPqjZG7w+EJ4T5TYApH5LM4s5NZVeXUMZn+0DZgTsCjorsib0VNbGVs6jCFrrsBv1U/bUhy2Qyt8fUCgb07yYYcEVuk5sqqpjwKBgQDUAcRV6xTO10edNyxvktj7hn6BtXafbfiNhC9xCjsTrIBUhsF1dG85VvsvgXLDmHbGvgVVsGI/Ka6X99mv5OOtlmF2YkFlfWpUci9mZCcnG7jKLDF/X6tRHuVtZdGMucoCqAnLuDw+5XvMnKvjgYBLTgT02ngOs+HKV+tGwKBgHf5EKuTCi/GnIPL7tS2d2kKksDP2PYZwW2JkNFJ6aicWhQyDAbJryToyjsR+dHOSno1wyptwxVePvlk3tOk2mGQlbvoLN4qXjMubk2wPBO686kCaUgLtyfP3b6v80SxTtlenTViTUQizuKA33ht07lBErT755YOMatcx8z6KUJAoGBAJYVhiN432sqdhUzKO73m3eLV+WFTCvDU1EDwf4ZSwc7eEHef/ghbFfvXGc1gq5kSRwudHAXc+fd7PKD2HBYGC3TdXHToMhtVNOsf7adjnjmV1gA5GERaoxVPuBjtOqX/Vq1TqaMQ5aKPQxyzlntDvDwsnzPj0EqDdoWrC/GtB/AoGAfhEIqXzhubeOtsYHOswkSI20PkhXSlNY9KARUiN5xOyqv7Z8IM2TXvSvQFiMoPVJqnj4/2sWGQh/UC23rXbhCsfW7NGloJhlKM76BptqFsn3EV6w4PN2Au2UtVa17qh8Mm2TbOZLBGxHTxecWTwuHu+w70bsgxOwvzTxgY=",
        "jevokit.crypto.aes-key=DemoAesKey123456",
        "jevokit.crypto.sm4-key=DemoSm4Key123456",
        "jevokit.api.client[0].id=wire-edge-client",
        "jevokit.api.client[0].secret-key=0123456789abcdef",
        // 演示 SM2 密钥对（与 sample application.yml 同源，仅用于测试）
        "jevokit.api.client[0].private-key=MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk5B6bcSXNwvtfd6nufsDHEPOu8N2hHKJMBW5blZP2eSgCgYIKoEcz1UBgi2hRANCAASxO58/zvEseRlMZtRlojzn7m4jAaZPTDSOalPCSk3nnGW0GmEu2ZhfWDNH+Nq8wA6BLodb2gwI9NF+F1Mp3p9R",
        "jevokit.api.client[0].public-key=MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEsTufP87xLHkZTGbUZaI85+5uIwGmT0w0jmpTwkpN55xltBphLtmYX1gzR/javMAOgS6HW9oMCPTRfhdTKd6fUQ==",
        "jevokit.api.client[0].verify=true",
        "jevokit.api.client[0].prevent-replay=false",
        "jevokit.api.client[0].decrypt=true",
        "jevokit.api.client[0].encrypt=true",
})
@DisplayName("上传 fileId 特殊字符真实线路（raw multipart → 嵌入式 Tomcat）")
class SecureUploadFilenameWireTest {

    private static final String URL_PATH = "/secure/upload";
    private static final String BOUNDARY = "----jevokitwiretest";

    @LocalServerPort
    int port;

    @Autowired
    JevokitApiProperties properties;

    // ------------------------------------------------------------------
    // 基线：原始 multipart 通道自证（clean push 走通才可归因后续用例）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("基线：手工 raw multipart（ASCII fileId）经真实容器全管线受理，code=0")
    void rawMultipartCleanPushAccepted() throws Exception {
        WireResult result = postPushWithFileId("file-1", "file-1");
        assertThat(result.httpStatus).as("clean 基线应 HTTP 200，实际响应：" + result.body).isEqualTo(200);
        assertThat(result.code).as("clean 基线应 code=0，实际 message：" + result.message).isEqualTo("0");
    }

    // ------------------------------------------------------------------
    // 中文 fileId / fileId 含 /：随 BUG-4/5 白名单收紧，客户端入口拒绝
    // ------------------------------------------------------------------

    @Test
    @DisplayName("中文 fileId：随 BUG-4/5 白名单收紧改为客户端拒绝（原 UTF-8 wire 往返受理的断言随行为收紧更新）")
    void chineseFileIdRejectedByClientWhitelist() {
        assertThatThrownBy(() -> JSecureClient.of(properties.getClient().get(0).getId())
                .upload(URL_PATH, Map.of("orderNo", "SO-WIRE-001"), Void.class)
                .transNo(UUID.randomUUID().toString())
                .add("wire-content".getBytes(StandardCharsets.UTF_8), "文件-001", "invoice", "发票.pdf"))
                .as("中文 fileId 不在 [A-Za-z0-9._-] 白名单内，应在客户端入口 fail-fast 拒绝")
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("fileId");
    }

    @Test
    @DisplayName("fileId 含 /：随 BUG-4/5 白名单收紧改为客户端拒绝（fileId 是对齐键而非路径，字符集统一收紧）")
    void slashFileIdRejectedByClientWhitelist() {
        assertThatThrownBy(() -> JSecureClient.of(properties.getClient().get(0).getId())
                .upload(URL_PATH, Map.of("orderNo", "SO-WIRE-001"), Void.class)
                .transNo(UUID.randomUUID().toString())
                .add("wire-content".getBytes(StandardCharsets.UTF_8), "nested/file-1", "invoice", "发票.pdf"))
                .as("含 / 的 fileId 应在客户端入口 fail-fast 拒绝（原 wire 对齐受理断言随行为收紧更新）")
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("fileId");
    }

    // ------------------------------------------------------------------
    // 恶意 filename：引号走私 / CRLF 注入不得破坏解析或骗过对齐
    // ------------------------------------------------------------------

    @Test
    @DisplayName("filename 引号走私：`f1\"; filename=\"smuggled` 原样写入时服务端必须拒绝（code!=0）")
    void quoteSmuggledFilenameRejectedOverRealWire() throws Exception {
        // 随 BUG-4/5 收紧：客户端入口已拒该 fileId，此处手工信封模拟攻击者完全控报文，
        // 验证服务端元数据层 fileId 白名单（新增）+ 对齐防线（原有）双道兜底
        String evilFileId = "f1\"; filename=\"smuggled";
        WireResult result = postPushWithFileId(evilFileId, evilFileId);
        System.out.println("[WIRE-DIAG] quote-smuggle -> http=" + result.httpStatus
                + " code=" + result.code + " message=" + result.message);
        assertThat(result.code)
                .as("走私出的第二个 filename 属性不得使报文被受理（实际响应：" + result.body + "）")
                .isNotEqualTo("0");
    }

    @Test
    @DisplayName("filename CRLF 注入：`f1\\r\\nX-Evil: 1` 原样写入时服务端拒绝且响应可预期（不受理/不 5xx）")
    void crlfInjectedFilenameSanelyRejectedOverRealWire() throws Exception {
        // 随 BUG-4/5 收紧：客户端入口已拒该 fileId，此处手工信封模拟攻击者完全控报文（防线同上双道）
        String evilFileId = "f1\r\nX-Evil: 1";
        WireResult result = postPushWithFileId(evilFileId, evilFileId);
        System.out.println("[WIRE-DIAG] crlf-inject -> http=" + result.httpStatus
                + " code=" + result.code + " message=" + result.message);
        assertThat(result.httpStatus).as("注入用例响应必须是可预期的 HTTP 完成状态").isLessThan(500);
        assertThat(result.code)
                .as("注入头不得使命名为 f1 的伪造 part 通过对齐（实际响应：" + result.body + "）")
                .isNotEqualTo("0");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * 以指定 fileId 发一次真实线路 push 上传（恶意 filename 场景专用）：
     * meta（声明恶意 fileId 与摘要）由<b>手工构造的加密信封</b>组装——不经 UploadChannel.add 的
     * fileId 白名单校验（随 BUG-4/5 收紧后客户端会 fail-fast 拒绝恶意值，无法再经门面组装，
     * 而本测试模拟的是"攻击者完全控报文"：自行加密签名、逐字节写死 Content-Disposition），
     * 文件 part 的 Content-Disposition filename 按 wireFilename 原样写入（不转义）。
     */
    private WireResult postPushWithFileId(String declaredFileId, String wireFilename) throws Exception {
        byte[] content = "wire-content".getBytes(StandardCharsets.UTF_8);
        JevokitApiClientProperties client = properties.getClient().get(0);

        JSecureUploadFileDetailDto entry = new JSecureUploadFileDetailDto();
        entry.setFileId(declaredFileId);
        entry.setFileType("invoice");
        entry.setFileName("发票.pdf");
        entry.setFormat("pdf");
        entry.setSize((long) content.length);
        entry.setDigest(JSecureUploadUtil.digestHex(content, JSecureUploadUtil.DIGEST_ALG_SHA256));
        entry.setDigestAlg(JSecureUploadUtil.DIGEST_ALG_SHA256);
        entry.setFetchMode(FetchMode.push);
        JSecureUploadDto<Map<String, Object>, JSecureUploadFileDetailDto> upload =
                new JSecureUploadDto<>(Map.of("orderNo", "SO-WIRE-001"), List.of(entry));
        JSecureRequest<Object> envelope = new JSecureRequest<>();
        envelope.setTransNo(UUID.randomUUID().toString());
        JSecureApiUtil.sealRequest(envelope, JSON.toJSONString(upload), client);
        Map<String, String> headers = JSecureApiUtil.buildProtocolHeaders(client);

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        // meta part：无 filename + application/json（协议约定形态）
        body.write(("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"meta\"\r\n"
                + "Content-Type: application/json\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(JSON.toJSONString(envelope).getBytes(StandardCharsets.UTF_8));
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        // 文件 part：filename 原样写入（wire 真话）
        body.write(("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"files\"; filename=\"" + wireFilename + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return postRaw(body.toByteArray(), headers);
    }

    /** 原始 multipart POST（协议头透传），回 HTTP 状态与协议 code/message。 */
    private WireResult postRaw(byte[] body, Map<String, String> headers) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + URL_PATH)
                .openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
        headers.forEach(connection::setRequestProperty);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body);
        }
        int status = connection.getResponseCode();
        InputStream stream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
        String responseBody = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        connection.disconnect();

        WireResult result = new WireResult();
        result.httpStatus = status;
        result.body = responseBody;
        try {
            com.alibaba.fastjson2.JSONObject envelope = JSON.parseObject(responseBody);
            if (envelope != null) {
                result.code = envelope.getString("code");
                result.message = envelope.getString("message");
            }
        } catch (Exception ignored) {
            // 非 JSON 响应体（容器错误页等）：code 留空，由调用方按"非受理"断言
        }
        return result;
    }

    /** 线路回执。 */
    private static final class WireResult {
        int httpStatus;
        String body;
        String code;
        String message;
    }

    /** 最小服务端装配（与 JSecureClientDefaultCarrierTest 同源手法）。 */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({RefreshAutoConfiguration.class, JevokitApiConfiguration.class, WireUploadController.class})
    static class MinimalWireApp {
    }

    /** 被测端点：meta + files 双参数形态。 */
    @RestController
    static class WireUploadController {

        @JSecureApi
        @PostMapping("/secure/upload")
        public JSecureResponse<String> upload(
                @RequestPart("meta") @Valid JSecureRequest<JSecureUploadDto<Map<String, Object>, JSecureUploadFileDetailDto>> meta,
                @RequestPart(value = "files", required = false) List<MultipartFile> files) {
            int declared = meta.getTarget().getFileDetails() == null ? 0 : meta.getTarget().getFileDetails().size();
            int bound = files == null ? 0 : files.size();
            return JSecureResponse.ok("accepted:" + declared + "/" + bound);
        }
    }
}
