package io.github.jevonsnotes.jevokit.api;

import jakarta.validation.Valid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.hamcrest.Matchers;
import org.springframework.web.multipart.MultipartFile;
import io.github.jevonsnotes.jevokit.api.annotation.JSecureApi;
import io.github.jevonsnotes.jevokit.api.client.JSecureClient;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiConfiguration;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiProperties;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.upload.JSecureUploadFileRefChecker;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * refer 模式正向切片：容器注册 {@link JSecureUploadFileRefChecker} 钩子后，refer 条目按钩子
 * 存在性判定——存在放行（code=0）、不存在拒绝（code=999）。同时实证终端级配置键
 * {@code jevokit.api.client[].upload-format-whitelist.<fileType>[n]} 的属性绑定（yaml 同构）。
 */
@WebMvcTest
@Import({RefreshAutoConfiguration.class, JevokitApiConfiguration.class,
        SecureUploadReferHookTest.ReferHookConfig.class, SecureUploadReferHookTest.MixedController.class})
@TestPropertySource(properties = {
        "jevokit.api.client[0].id=refer-test-client",
        "jevokit.api.client[0].secret-key=0123456789abcdef",
        "jevokit.api.client[0].private-key=MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk5B6bcSXNwvtfd6nufsDHEPOu8N2hHKJMBW5blZP2eSgCgYIKoEcz1UBgi2hRANCAASxO58/zvEseRlMZtRlojzn7m4jAaZPTDSOalPCSk3nnGW0GmEu2ZhfWDNH+Nq8wA6BLodb2gwI9NF+F1Mp3p9R",
        "jevokit.api.client[0].public-key=MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEsTufP87xLHkZTGbUZaI85+5uIwGmT0w0jmpTwkpN55xltBphLtmYX1gzR/javMAOgS6HW9oMCPTRfhdTKd6fUQ==",
        "jevokit.api.client[0].verify=true",
        "jevokit.api.client[0].prevent-replay=false",
        // 终端级 format 白名单属性绑定实证（pdf/jpg/png 演示值；生产必须按业务收紧）
        "jevokit.api.client[0].upload-format-whitelist.invoice[0]=pdf",
        "jevokit.api.client[0].upload-format-whitelist.invoice[1]=jpg",
        "jevokit.api.client[0].upload-format-whitelist.id_card[0]=jpg",
})
@DisplayName("JSecure 上传 refer 钩子切片")
class SecureUploadReferHookTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JevokitApiProperties properties;

    @Test
    @DisplayName("refer 正向：钩子存在且文件存在放行（混合 push+refer），code=0")
    void referEntryWithExistingFileAccepted() throws Exception {
        byte[] content = "invoice-bytes".getBytes(StandardCharsets.UTF_8);
        JSecureUploadRequest request = JSecureClient.of(client().getId())
                .upload("/secure/upload-mixed", "mixed-biz", Void.class)
                .add(content, "file-1", "invoice", "发票.pdf")
                .refer("archived-1")
                .transNo(UUID.randomUUID().toString())
                .prepare().getRequest();
        perform(request).andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));
    }

    @Test
    @DisplayName("refer 必拒：钩子存在但业务判定文件不存在，code=999 refer 文件不存在")
    void referEntryWithMissingFileRejected() throws Exception {
        JSecureUploadRequest request = JSecureClient.of(client().getId())
                .upload("/secure/upload-mixed", "refer-biz", Void.class)
                .refer("archived-not-exist")
                .transNo(UUID.randomUUID().toString())
                .prepare().getRequest();
        perform(request)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message", Matchers.containsString("refer 文件不存在")));
    }

    @Test
    @DisplayName("format 白名单属性绑定生效：invoice 允许 pdf/jpg，申报 txt 必拒")
    void formatWhitelistBindingWorks() throws Exception {
        byte[] content = "txt-bytes".getBytes(StandardCharsets.UTF_8);
        JSecureUploadRequest request = JSecureClient.of(client().getId())
                .upload("/secure/upload-mixed", "biz", Void.class)
                .add(content, "file-1", "invoice", "note.txt")
                .transNo(UUID.randomUUID().toString())
                .prepare().getRequest();
        perform(request)
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("999"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message", Matchers.containsString("格式不在白名单")));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private JevokitApiClientProperties client() {
        return properties.getClient().stream()
                .filter(c -> "refer-test-client".equals(c.getId())).findFirst().orElseThrow();
    }

    private org.springframework.test.web.servlet.ResultActions perform(JSecureUploadRequest request) throws Exception {
        MockPart metaPart = new MockPart("meta", null,
                request.getMetaJson().getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON);
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart("/secure/upload-mixed").part(metaPart);
        request.getHeaders().forEach(builder::header);
        request.getFiles().forEach(file -> builder.file(
                new org.springframework.mock.web.MockMultipartFile("files", file.getFileId(), file.getContentType(), file.getContent())));
        return mockMvc.perform(builder)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").exists());
    }

    /** 演示钩子：archived-1 视为存在，其余不存在。 */
    @TestConfiguration
    static class ReferHookConfig {
        @Bean
        JSecureUploadFileRefChecker demoRefChecker() {
            return file -> Set.of("archived-1").contains(file.getFileId());
        }
    }

    /** 被测端点：混合 push + refer。 */
    @RestController
    static class MixedController {

        @JSecureApi
        @PostMapping("/secure/upload-mixed")
        public JSecureResponse<String> upload(
                @RequestPart("meta") @Valid JSecureRequest<JSecureUploadDto<String, JSecureUploadFileDto>> meta,
                @RequestPart(value = "files", required = false) List<MultipartFile> files) {
            int declared = meta.getTarget().getFiles() == null ? 0 : meta.getTarget().getFiles().size();
            int bound = files == null ? 0 : files.size();
            return JSecureResponse.ok("accepted:" + declared + "/" + bound);
        }
    }
}
