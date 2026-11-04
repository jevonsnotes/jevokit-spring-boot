package io.github.jevonsnotes.jevokit.api.upload;

import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上传配置启动期顾问纯函数单测：告警收集口径（开关弱化/双关/算法笔误/容器配平失当）。
 */
@DisplayName("上传配置启动顾问")
class JSecureUploadStartupAdvisorTest {

    private JevokitApiClientProperties client(String id) {
        JevokitApiClientProperties c = new JevokitApiClientProperties();
        c.setId(id);
        return c;
    }

    @Test
    @DisplayName("全缺省配置：零告警")
    void defaultsProduceNoWarning() {
        List<String> warnings = JSecureUploadStartupAdvisor.collectWarnings(List.of(client("c1")), null);
        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("digest 关：提示跳过范围；size 关：提示跳过范围；双关：提示无任何内容完整性校验")
    void switchOffWarnings() {
        JevokitApiClientProperties digestOff = client("digest-off");
        digestOff.setUploadDigestVerify(false);
        assertThat(JSecureUploadStartupAdvisor.collectWarnings(List.of(digestOff), null))
                .singleElement().asString().contains("upload-digest-verify=false").contains("digest-off");

        JevokitApiClientProperties sizeOff = client("size-off");
        sizeOff.setUploadSizeVerify(false);
        assertThat(JSecureUploadStartupAdvisor.collectWarnings(List.of(sizeOff), null))
                .singleElement().asString().contains("upload-size-verify=false").contains("size-off");

        JevokitApiClientProperties bothOff = client("both-off");
        bothOff.setUploadDigestVerify(false);
        bothOff.setUploadSizeVerify(false);
        assertThat(JSecureUploadStartupAdvisor.collectWarnings(List.of(bothOff), null))
                .singleElement().asString().contains("均为 false").contains("无任何完整性校验");
    }

    @Test
    @DisplayName("算法范围含笔误名：提示全集与收窄后果（大小写/空白容错，合法名不误报）")
    void bogusAlgorithmWarning() {
        JevokitApiClientProperties c = client("typo");
        c.setUploadDigestAlgorithms(List.of(" sm3 ", "MD5", "sha-256", "sha512"));
        List<String> warnings = JSecureUploadStartupAdvisor.collectWarnings(List.of(c), null);
        // 笔误清单精确列出非法名（合法名 sm3/MD5 不入笔误清单；全集列表里出现不算）
        assertThat(warnings).singleElement().asString()
                .contains("算法名 [sha-256, sha512]");
    }

    @Test
    @DisplayName("容器 max-file-size > upload-max-size：配平失当告警（相等/更小不告警，无容器环境跳过）")
    void containerMismatchWarning() {
        JevokitApiClientProperties c = client("mismatch");
        c.setUploadMaxSize(5L * 1024 * 1024);
        assertThat(JSecureUploadStartupAdvisor.collectWarnings(List.of(c), DataSize.ofMegabytes(100)))
                .singleElement().asString()
                .contains("max-file-size").contains("100MB").contains("5MB").contains("配平");
        // 人性化：字节数不裸奔（104857600B 之类不出现）
        assertThat(JSecureUploadStartupAdvisor.collectWarnings(List.of(c), DataSize.ofBytes(104_857_600)))
                .singleElement().asString().contains("100MB").doesNotContain("104857600B");
        assertThat(JSecureUploadStartupAdvisor.collectWarnings(List.of(c), DataSize.ofMegabytes(5))).isEmpty();
        assertThat(JSecureUploadStartupAdvisor.collectWarnings(List.of(c), null)).isEmpty();
    }
}
