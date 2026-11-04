package io.github.jevonsnotes.jevokit.api.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.github.jevonsnotes.jevokit.api.dto.FetchMode;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDetailDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上传协议链路日志脱敏器测试：fetchToken 前 4 后 4 明文、url 查询串整体遮蔽，
 * maskedJson 不含任何敏感原文。
 */
@DisplayName("上传日志脱敏器")
class JSecureUploadLogMaskerTest {

    @Test
    @DisplayName("脱敏器：fetchToken 前4后4、url 查询串遮蔽、maskedJson 不含原文")
    void logMaskerMasksSensitiveFields() {
        assertThat(JSecureUploadLogMasker.maskToken("abcdefghijklmnop")).isEqualTo("abcd****mnop");
        assertThat(JSecureUploadLogMasker.maskToken("short")).isEqualTo("sh****");

        JSecureUploadFileDetailDto file = new JSecureUploadFileDetailDto();
        file.setFileId("f1");
        file.setUrl("https://cdn.partner.com/a.pdf?sign=abc&token=xyz");
        file.setFetchToken("secret-fetch-token-9876");
        file.setFetchMode(FetchMode.pull);
        String maskedJson = JSecureUploadLogMasker.maskedJson(new JSecureUploadDto<>("biz", List.of(file)));
        assertThat(maskedJson).contains("secr****9876");                    // 前4后4明文 + 掩码
        assertThat(maskedJson).doesNotContain("secret-fetch-token-9876");   // 原文不得出现
        assertThat(maskedJson).doesNotContain("sign=abc");                  // 查询串不得出现
        assertThat(maskedJson).contains("https://cdn.partner.com/a.pdf?***");
    }
}
