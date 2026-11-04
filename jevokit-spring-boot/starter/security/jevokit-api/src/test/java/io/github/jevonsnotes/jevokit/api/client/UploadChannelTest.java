package io.github.jevonsnotes.jevokit.api.client;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiProperties;
import io.github.jevonsnotes.jevokit.api.dto.FetchMode;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDetailDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadRequest;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;
import io.github.jevonsnotes.jevokit.core.util.SecurityUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 上传通道（{@link UploadChannel}）组装测试：自动摘要清单（size/format/digest）、fileId 唯一性、
 * prepare 产物经服务端同源配方（SM4 解密 + SM2 验签 + 反序列化）完整还原——即客户端-服务端往返闭环。
 */
@DisplayName("上传通道组装")
class UploadChannelTest {

    /** 演示 SM2 密钥对（与 sample application.yml 同源，仅用于测试）。 */
    private static final String PRIVATE_KEY = "MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk5B6bcSXNwvtfd6nufsDHEPOu8N2hHKJMBW5blZP2eSgCgYIKoEcz1UBgi2hRANCAASxO58/zvEseRlMZtRlojzn7m4jAaZPTDSOalPCSk3nnGW0GmEu2ZhfWDNH+Nq8wA6BLodb2gwI9NF+F1Mp3p9R";
    private static final String PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEsTufP87xLHkZTGbUZaI85+5uIwGmT0w0jmpTwkpN55xltBphLtmYX1gzR/javMAOgS6HW9oMCPTRfhdTKd6fUQ==";
    private static final String CLIENT_ID = "channel-test-client";

    private static JevokitApiClientProperties client;

    @BeforeAll
    static void registerDemoClient() {
        client = new JevokitApiClientProperties();
        client.setId(CLIENT_ID);
        client.setSecretKey("0123456789abcdef");
        client.setPrivateKey(PRIVATE_KEY);
        client.setPublicKey(PUBLIC_KEY);
        JevokitApiProperties properties = new JevokitApiProperties();
        properties.setClient(new ArrayList<>(List.of(client)));
        // 注册静态配置（脱离 Spring 环境启用 JSecureApiUtil.getClient）
        new JSecureApiUtil(properties);
    }

    @Test
    @DisplayName("add 自动计算 size/format/digest（sha256），文件 part filename=fileId")
    void addComputesMetadata() {
        byte[] content = "invoice-bytes".getBytes(StandardCharsets.UTF_8);
        JSecureUploadRequest request = channel("biz")
                .add(content, "file-1", "invoice", "发票.PDF")
                .prepare()
                .getRequest();

        assertThat(request.getFiles()).hasSize(1);
        assertThat(request.getFiles().get(0).getFileId()).isEqualTo("file-1");
        assertThat(request.getFiles().get(0).partName()).isEqualTo("files");
        assertThat(request.getFiles().get(0).getContent()).isEqualTo(content);

        JSecureUploadFileDetailDto entry = parseEntries(request).get(0);
        assertThat(entry.getSize()).isEqualTo((long) content.length);
        assertThat(entry.getFormat()).isEqualTo("pdf");
        assertThat(entry.getDigestAlg()).isEqualTo("sha256");
        assertThat(entry.getDigest()).isEqualTo(
                io.github.jevonsnotes.jevokit.api.upload.JSecureUploadUtil.digestHex(content, "sha256"));
        assertThat(entry.getFetchMode()).isEqualTo(FetchMode.push);
    }

    @Test
    @DisplayName("fileId 重复 fail-fast")
    void duplicateFileIdRejected() {
        assertThatThrownBy(() -> channel("biz")
                .add(new byte[1], "file-1", "invoice")
                .add(new byte[2], "file-1", "id_card"))
                .isInstanceOf(JSecureApiException.class)
                .hasMessageContaining("重复");
    }

    @Test
    @DisplayName("prepare 产物服务端同源还原：SM4 解密 + SM2 验签 + 反序列化闭环（push/pull/refer 混合）")
    void prepareRoundTripsServerSide() {
        JSecureUploadRequest request = channel(Map.of("orderNo", "SO-1"))
                .transNo("T-001").transType("UPLOAD")
                .add("invoice-bytes".getBytes(StandardCharsets.UTF_8), "file-1", "invoice", "发票.pdf")
                .pull("remote-1", "https://cdn.partner.com/a.pdf", 3L, null, "invoice", "pdf")
                .refer("archived-1")
                .prepare()
                .getRequest();

        // 协议头四件套
        Set<String> headerNames = request.getHeaders().keySet();
        assertThat(headerNames).containsExactlyInAnyOrder("x-api-client-id", "x-api-timestamp", "x-api-nonce", "x-api-signature");
        assertThat(request.getHeaders().get("x-api-client-id")).isEqualTo(client.getId());

        // 服务端配方还原：解析信封 → 解密 view → 验签 → 反序列化
        JSONObject envelope = JSONObject.parseObject(request.getMetaJson());
        assertThat(envelope.getString("transNo")).isEqualTo("T-001");
        assertThat(envelope.getString("transType")).isEqualTo("UPLOAD");
        String view = SecurityUtil.sm4cbcDecrypt(envelope.getString("data"), client.getSecretKeySm4());
        assertThat(SecurityUtil.sm2Base64Verify(view, envelope.getString("signature"), client.getPublicKey()))
                .as("SM2 体签名应通过").isTrue();

        JSecureUploadDto<Object, JSecureUploadFileDetailDto> upload =
                JSONObject.parseObject(view, JSecureUploadDto.class);
        assertThat((Map<String, Object>) upload.getBusinessData()).containsEntry("orderNo", "SO-1");
        assertThat(upload.getFileDetails()).hasSize(3);
        assertThat(upload.getFileDetails().get(0).getFetchMode()).isEqualTo(FetchMode.push);
        assertThat(upload.getFileDetails().get(1).getUrl()).isEqualTo("https://cdn.partner.com/a.pdf");
        assertThat(upload.getFileDetails().get(2).getFileId()).isEqualTo("archived-1");
    }

    @Test
    @DisplayName("业务数据为 Map 时零类接入（简式）")
    void mapBusinessDataSupported() {
        JSecureUploadRequest request = channel(Map.of("k", "v"))
                .add(new byte[]{1}, "f", "invoice")
                .prepare()
                .getRequest();
        assertThat(parseEntries(request)).hasSize(1);
        assertThat(request.getMetaJson()).contains("\"data\"");
    }

    @Test
    @DisplayName("prepare 幂等：重复调用不重建产物（篡改检测用）")
    void prepareIsIdempotent() {
        UploadChannel<String, Void> channel = channel("biz").add(new byte[]{1}, "f", "invoice").prepare();
        JSecureUploadRequest first = channel.getRequest();
        channel.prepare();
        assertThat(channel.getRequest()).isSameAs(first);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 造一个上传通道（响应类型无关，本类只测组装不发送）。 */
    private static <B> UploadChannel<B, Void> channel(B businessData) {
        return JSecureClient.of(CLIENT_ID).upload("/secure/upload", businessData, Void.class);
    }

    /** 解出构建产物里的文件清单（服务端配方解密）。 */
    private List<JSecureUploadFileDetailDto> parseEntries(JSecureUploadRequest request) {
        JSONObject envelope = JSONObject.parseObject(request.getMetaJson());
        String view = SecurityUtil.sm4cbcDecrypt(envelope.getString("data"), client.getSecretKeySm4());
        return JSONObject.parseObject(view, JSecureUploadDto.class).getFileDetails();
    }
}
