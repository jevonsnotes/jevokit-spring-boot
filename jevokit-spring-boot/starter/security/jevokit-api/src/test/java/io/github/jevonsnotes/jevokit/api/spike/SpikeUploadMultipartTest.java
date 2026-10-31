package io.github.jevonsnotes.jevokit.api.spike;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import io.github.jevonsnotes.jevokit.api.advice.DecryptRequestAdviceAdapter;
import io.github.jevonsnotes.jevokit.api.annotation.JSecureApi;
import io.github.jevonsnotes.jevokit.api.client.JSecureClient;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiProperties;
import io.github.jevonsnotes.jevokit.api.dto.FetchMode;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDto;
import io.github.jevonsnotes.jevokit.api.service.IPreventReplayAttack;
import io.github.jevonsnotes.jevokit.api.service.impl.DefaultPreventReplayAttack;
import io.github.jevonsnotes.jevokit.api.upload.JSecureUploadFileRefChecker;
import io.github.jevonsnotes.jevokit.api.upload.JSecureUploadVerifier;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶段一 Spike 实证（文件上传接入 JSecure 协议的三条前置验证，通过后才进入实施）：
 * <ol>
 *   <li><b>命门：@RequestPart 是否触发 RequestBodyAdvice</b>——meta part（application/json）
 *       经消息转换器读取时是否走进 DecryptRequestAdviceAdapter 的 supports/beforeBodyRead/afterBodyRead，
 *       且 supports() 对 JSecureRequest&lt;JSecureUploadDto&lt;A,B&gt;&gt; 嵌套泛型命中；</li>
 *   <li><b>multipart 时序</b>——afterBodyRead 执行时通过 RequestContextHolder 取当前请求的文件 part
 *       是否已就位；同名 "files" 多文件绑定 List&lt;MultipartFile&gt; 的行为；meta part 是否混入该 List；</li>
 *   <li><b>fastjson2 嵌套泛型</b>——JSONObject.parseObject(view, type) 对两层泛型
 *       JSecureUploadDto&lt;OrderBiz, JSecureUploadFileDto&gt; 的反序列化正确性
 *       （advice 内 actualTypeArguments[0] 是 ParameterizedType）。</li>
 * </ol>
 * 说明：本类是实施前的一次性实证，结论沉淀进组件 README；实施落地后由正式测试类取代。
 */
@WebMvcTest
@Import({SpikeUploadMultipartTest.SpikeUploadWebConfig.class, SpikeUploadMultipartTest.SpikeUploadController.class})
@TestPropertySource(properties = {
        "jevokit.api.client[0].id=spike-client",
        "jevokit.api.client[0].secret-key=0123456789abcdef",
        "jevokit.api.client[0].private-key=MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk5B6bcSXNwvtfd6nufsDHEPOu8N2hHKJMBW5blZP2eSgCgYIKoEcz1UBgi2hRANCAASxO58/zvEseRlMZtRlojzn7m4jAaZPTDSOalPCSk3nnGW0GmEu2ZhfWDNH+Nq8wA6BLodb2gwI9NF+F1Mp3p9R",
        "jevokit.api.client[0].public-key=MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEsTufP87xLHkZTGbUZaI85+5uIwGmT0w0jmpTwkpN55xltBphLtmYX1gzR/javMAOgS6HW9oMCPTRfhdTKd6fUQ==",
        "jevokit.api.client[0].verify=true",
        "jevokit.api.client[0].prevent-replay=false",
        "jevokit.api.client[0].decrypt=true",
        "jevokit.api.client[0].encrypt=true",
})
@DisplayName("Spike：@RequestPart 协议 advice 触发 + multipart 时序 + 嵌套泛型")
class SpikeUploadMultipartTest {

    private static final String CLIENT_ID = "spike-client";

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void resetSpikeState() {
        RecordingDecryptAdvice.reset();
        SpikeUploadController.reset();
    }

    // ------------------------------------------------------------------
    // Spike 1：@RequestPart 触发 RequestBodyAdvice（命门）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Spike1：meta part 走 @RequestPart 时 supports/beforeBodyRead/afterBodyRead 均被调用且嵌套泛型命中")
    void requestPartTriggersRequestBodyAdvice() throws Exception {
        org.springframework.mock.web.MockPart metaPart = new org.springframework.mock.web.MockPart(
                "meta", null, buildMetaEnvelopeJson().getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON);

        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart("/spike/upload")
                .part(metaPart)
                .file(new org.springframework.mock.web.MockMultipartFile("files", "file-1", "application/octet-stream", "invoice-bytes".getBytes(StandardCharsets.UTF_8)))
                .file(new org.springframework.mock.web.MockMultipartFile("files", "file-2", "application/octet-stream", "idcard-bytes".getBytes(StandardCharsets.UTF_8)));
        prepareProtocolHeaders(builder);

        String response = mockMvc.perform(builder)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"))
                .andReturn().getResponse().getContentAsString();

        // 命门结论 1：advice 链条三阶段全部被调用
        assertThat(RecordingDecryptAdvice.supportsCalled).as("supports() 应被调用且命中").isTrue();
        assertThat(RecordingDecryptAdvice.beforeBodyReadCalled).as("beforeBodyRead 应被调用").isTrue();
        assertThat(RecordingDecryptAdvice.afterBodyReadCalled).as("afterBodyRead 应被调用").isTrue();

        // 命门结论 2：嵌套泛型反序列化正确（业务数据 + 文件清单均还原）
        assertThat(SpikeUploadController.capturedTarget).as("target 应为 JSecureUploadDto").isNotNull();
        assertThat(SpikeUploadController.capturedTarget.getBusinessData().getOrderNo()).isEqualTo("SO-2026-001");
        assertThat(SpikeUploadController.capturedTarget.getFiles()).hasSize(2);
        assertThat(SpikeUploadController.capturedTarget.getFiles().get(0).getFileId()).isEqualTo("file-1");
        assertThat(SpikeUploadController.capturedTarget.getFiles().get(0).getFetchMode()).isEqualTo(FetchMode.push);
        assertThat(SpikeUploadController.capturedTarget.getFiles().get(1).getDigestAlg()).isEqualTo("sm3");

        System.out.println("[SPIKE-1] PASS @RequestPart 触发 advice 链，response=" + response);
    }

    // ------------------------------------------------------------------
    // Spike 2：multipart 时序 + List<MultipartFile> 绑定 + meta part 混入情况
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Spike2：afterBodyRead 时文件 part 已就位；List<MultipartFile> 同名绑定；meta 不混入")
    void multipartPartsReadyAtAfterBodyReadAndListBinding() throws Exception {
        org.springframework.mock.web.MockPart metaPart = new org.springframework.mock.web.MockPart(
                "meta", null, buildMetaEnvelopeJson().getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON);

        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart("/spike/upload")
                .part(metaPart)
                .file(new org.springframework.mock.web.MockMultipartFile("files", "file-1", "application/octet-stream", "invoice-bytes".getBytes()))
                .file(new org.springframework.mock.web.MockMultipartFile("files", "file-2", "application/octet-stream", "idcard-bytes".getBytes()));
        prepareProtocolHeaders(builder);

        mockMvc.perform(builder)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"));

        // 时序结论：afterBodyRead 执行时经 RequestContextHolder 取根请求，文件 part 已就位。
        // MockMvc 环境差异（spike 实证）：.file() 附加的文件不在根请求 getParts()（其中仅 meta part），
        // 而在 MultipartHttpServletRequest.getMultiFileMap()；真实容器则统一在 getParts()。
        // → 采集口径必须双源合并（getParts() 过滤 filename + multiFileMap，按 name+filename 去重），
        //   即 JSecureUploadVerifier.collectFileParts 的实现方式。
        assertThat(RecordingDecryptAdvice.rootRequestPresent).as("RequestContextHolder 应能取到当前请求").isTrue();
        assertThat(RecordingDecryptAdvice.partNamesInAfterBodyRead)
                .as("MockMvc 下根请求 getParts() 仅含 .part() 附加的 meta")
                .containsExactly("meta");
        assertThat(RecordingDecryptAdvice.multipartFileNames)
                .as("合并视图（multiFileMap）应含两个文件")
                .containsExactlyInAnyOrder("file-1", "file-2");

        // 文件 part 收集口径：filename != null 的 part 即文件 part（meta 无 filename，天然排除）
        System.out.println("[SPIKE-2] getParts() 全量 = " + RecordingDecryptAdvice.partNameAndFileNamePairs);

        // List<MultipartFile> 绑定：同名 "files" 两个 part 均注入，filename 各自不同，meta 不混入
        assertThat(SpikeUploadController.boundFilesSize).as("List<MultipartFile> 应绑到 2 个文件").isEqualTo(2);
        assertThat(SpikeUploadController.boundFileNames)
                .as("绑定文件名应与 part filename 一致且不含 meta")
                .containsExactlyInAnyOrder("file-1", "file-2");

        System.out.println("[SPIKE-2] PASS multipart 时序与绑定行为符合预期");
    }

    // ------------------------------------------------------------------
    // Spike 3：fastjson2 嵌套泛型反序列化（无 Spring 依赖的纯解析验证）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Spike3：fastjson2 对 JSecureUploadDto<A,B> 两层泛型按 actualTypeArguments[0] 正确反序列化")
    void fastjson2NestedGenericParsing() throws Exception {
        JSecureUploadDto<SpikeOrderBiz, JSecureUploadFileDto> dto = buildUploadDto();
        String view = JSON.toJSONString(dto);

        // 完全复刻 advice 的取型路径：控制器方法泛型参数 → actualTypeArguments[0]
        Method method = SpikeUploadController.class.getMethod(
                "upload", JSecureRequest.class, List.class);
        Type targetType = method.getGenericParameterTypes()[0];
        assertThat(targetType).as("控制器参数应为参数化类型").isInstanceOf(ParameterizedType.class);
        Type innerType = ((ParameterizedType) targetType).getActualTypeArguments()[0];
        assertThat(innerType).as("actualTypeArguments[0] 应为 ParameterizedType").isInstanceOf(ParameterizedType.class);

        Object parsed = JSONObject.parseObject(view, innerType);
        assertThat(parsed).as("反序列化产物应为 JSecureUploadDto").isInstanceOf(JSecureUploadDto.class);

        @SuppressWarnings("unchecked")
        JSecureUploadDto<SpikeOrderBiz, JSecureUploadFileDto> back =
                (JSecureUploadDto<SpikeOrderBiz, JSecureUploadFileDto>) parsed;
        assertThat(back.getBusinessData().getOrderNo()).as("业务数据（泛型 B）应正确还原").isEqualTo("SO-2026-001");
        assertThat(back.getBusinessData().getAmount()).isEqualTo(199.00);
        assertThat(back.getFiles()).as("文件清单（泛型 F）应正确还原").hasSize(2);
        assertThat(back.getFiles().get(0).getFileId()).isEqualTo("file-1");
        assertThat(back.getFiles().get(0).getSize()).isEqualTo(13L);
        assertThat(back.getFiles().get(1).getFileType()).isEqualTo("id_card");
        assertThat(back.getFiles().get(1).getDigestAlg()).isEqualTo("sm3");
        assertThat(back.getFiles().get(1).getExtra()).containsEntry("pageNo", "2");

        // supports() 对嵌套泛型命中（同一取型路径）
        MethodParameter methodParameter = new MethodParameter(method, 0);
        ObjectProvider<JSecureUploadFileRefChecker> emptyRefCheckerProvider =
                new org.springframework.beans.factory.support.DefaultListableBeanFactory()
                        .getBeanProvider(JSecureUploadFileRefChecker.class);
        RecordingDecryptAdvice advice = new RecordingDecryptAdvice(
                new JevokitApiProperties(), (nonce, ts) -> false, new JSecureUploadVerifier(emptyRefCheckerProvider));
        assertThat(advice.supports(methodParameter, targetType,
                org.springframework.http.converter.json.MappingJackson2HttpMessageConverter.class))
                .as("supports() 对 JSecureRequest<JSecureUploadDto<A,B>> 应命中")
                .isTrue();

        System.out.println("[SPIKE-3] PASS fastjson2 嵌套泛型反序列化正确");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 组装 meta part 的协议信封（真实客户端路径：JSecureClient JSON 通道 prepare() 的产物）。 */
    private String buildMetaEnvelopeJson() {
        return JSON.toJSONString(JSecureClient.of(CLIENT_ID)
                .post("/spike/upload", buildUploadDto(), Void.class)
                .transNo(UUID.randomUUID().toString())
                .prepare()
                .getRequest());
    }

    /** 组装上传业务体（push 双文件：发票 + 身份证，混合 sha256/sm3 两种摘要算法）。 */
    private JSecureUploadDto<SpikeOrderBiz, JSecureUploadFileDto> buildUploadDto() {
        JSecureUploadFileDto f1 = new JSecureUploadFileDto();
        f1.setFileId("file-1");
        f1.setFileType("invoice");
        f1.setUsage("main");
        f1.setFileName("发票.pdf");
        f1.setFormat("pdf");
        f1.setSize(13L);
        f1.setDigest(sha256Hex("invoice-bytes"));
        f1.setDigestAlg("sha256");
        f1.setFetchMode(FetchMode.push);

        JSecureUploadFileDto f2 = new JSecureUploadFileDto();
        f2.setFileId("file-2");
        f2.setFileType("id_card");
        f2.setUsage("back");
        f2.setFileName("身份证背面.jpg");
        f2.setFormat("jpg");
        f2.setSize(12L);
        f2.setDigest(sm3Hex("idcard-bytes"));
        f2.setDigestAlg("sm3");
        f2.setFetchMode(FetchMode.push);
        f2.setExtra(Map.of("pageNo", "2"));

        SpikeOrderBiz biz = new SpikeOrderBiz();
        biz.setOrderNo("SO-2026-001");
        biz.setAmount(199.00);
        return new JSecureUploadDto<>(biz, List.of(f1, f2));
    }

    /** 在根请求上放置协议头（client-id/timestamp/nonce/signature，与信封客户端同源）。 */
    private void prepareProtocolHeaders(MockMultipartHttpServletRequestBuilder builder) {
        JevokitApiClientProperties client = JSecureApiUtil.getClient(CLIENT_ID);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = UUID.randomUUID().toString();
        String signature = JSecureApiUtil.headerSignature(
                client.getHeader().getSignAlg(), timestamp, nonce, client.getSecretKey());
        builder.header(JSecureApiUtil.rebuildHeader(client.getHeader().getClientIdName(), client.getHeader().getPrefix()), CLIENT_ID);
        builder.header(JSecureApiUtil.rebuildHeader(client.getHeader().getTimestampName(), client.getHeader().getPrefix()), timestamp);
        builder.header(JSecureApiUtil.rebuildHeader(client.getHeader().getNonceName(), client.getHeader().getPrefix()), nonce);
        builder.header(JSecureApiUtil.rebuildHeader(client.getHeader().getSignatureName(), client.getHeader().getPrefix()), signature);
    }

    private static String sha256Hex(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sm3Hex(String content) {
        return new cn.hutool.crypto.digest.SM3().digestHex(content);
    }

    /** 业务数据样例（泛型 B 的具体类型）。 */
    public static class SpikeOrderBiz {
        private String orderNo;
        private Double amount;

        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
    }

    /** 被测端点：meta（协议信封）+ files（文件 part）双参数形态。 */
    @RestController
    static class SpikeUploadController {

        static JSecureUploadDto<SpikeOrderBiz, JSecureUploadFileDto> capturedTarget;
        static int boundFilesSize;
        static List<String> boundFileNames;

        static void reset() {
            capturedTarget = null;
            boundFilesSize = -1;
            boundFileNames = null;
        }

        @JSecureApi
        @PostMapping("/spike/upload")
        public JSecureResponse<String> upload(
                @RequestPart("meta") @jakarta.validation.Valid JSecureRequest<JSecureUploadDto<SpikeOrderBiz, JSecureUploadFileDto>> meta,
                @RequestPart("files") List<MultipartFile> files) {
            capturedTarget = meta.getTarget();
            boundFilesSize = files.size();
            boundFileNames = files.stream().map(MultipartFile::getOriginalFilename).toList();
            return JSecureResponse.ok("accepted");
        }
    }

    /** 录制型解密 advice：透传全部协议处理，仅记录三阶段调用与 part 可见性。 */
    @ControllerAdvice
    static class RecordingDecryptAdvice extends DecryptRequestAdviceAdapter {

        static boolean supportsCalled;
        static boolean beforeBodyReadCalled;
        static boolean afterBodyReadCalled;
        static boolean rootRequestPresent;
        static List<String> partNamesInAfterBodyRead;
        static List<String> partNamesWithFileName;
        static List<String> partNameAndFileNamePairs;
        static List<String> multipartFileNames;

        RecordingDecryptAdvice(JevokitApiProperties properties, IPreventReplayAttack iPreventReplayAttack,
                               JSecureUploadVerifier uploadVerifier) {
            super(properties, iPreventReplayAttack, uploadVerifier);
        }

        static void reset() {
            supportsCalled = false;
            beforeBodyReadCalled = false;
            afterBodyReadCalled = false;
            rootRequestPresent = false;
            partNamesInAfterBodyRead = null;
            partNamesWithFileName = null;
            partNameAndFileNamePairs = null;
            multipartFileNames = null;
        }

        @Override
        public boolean supports(MethodParameter methodParameter, Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {
            boolean result = super.supports(methodParameter, targetType, converterType);
            if (result) {
                supportsCalled = true;
            }
            return result;
        }

        @Override
        public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
                                               Type targetType,
                                               Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
            beforeBodyReadCalled = true;
            // 记录 part 包装器的请求头（预期：只有 part 级头，无根请求协议头）
            System.out.println("[SPIKE] beforeBodyRead inputMessage(part) headers = " + inputMessage.getHeaders().keySet());
            return super.beforeBodyRead(inputMessage, parameter, targetType, converterType);
        }

        @Override
        public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                    Type targetType,
                                    Class<? extends HttpMessageConverter<?>> converterType) {
            afterBodyReadCalled = true;
            java.util.Optional<HttpServletRequest> current = JSecureApiUtil.getCurrentRequest();
            rootRequestPresent = current.isPresent();
            current.ifPresent(request -> {
                try {
                    List<String> names = new ArrayList<>();
                    List<String> withFileName = new ArrayList<>();
                    List<String> pairs = new ArrayList<>();
                    for (Part part : request.getParts()) {
                        names.add(part.getName());
                        pairs.add(part.getName() + "->" + part.getSubmittedFileName());
                        if (part.getSubmittedFileName() != null) {
                            withFileName.add(part.getName());
                        }
                    }
                    partNamesInAfterBodyRead = names;
                    partNamesWithFileName = withFileName;
                    partNameAndFileNamePairs = pairs;
                    if (request instanceof MultipartHttpServletRequest multipartRequest) {
                        multipartFileNames = multipartRequest.getMultiFileMap().values().stream()
                                .flatMap(List::stream)
                                .map(MultipartFile::getOriginalFilename)
                                .toList();
                        System.out.println("[SPIKE] 根请求 instanceof MultipartHttpServletRequest = true, multiFileMap = " + multipartFileNames);
                    } else {
                        System.out.println("[SPIKE] 根请求非 MultipartHttpServletRequest：" + request.getClass().getName());
                    }
                } catch (Exception e) {
                    System.out.println("[SPIKE] getParts() 异常：" + e);
                }
            });
            return super.afterBodyRead(body, inputMessage, parameter, targetType, converterType);
        }
    }

    /** 最小装配：属性绑定 + 防重放 + 协议工具 + 录制型 advice（不引 JevokitApiConfiguration，避免重复 advice）。 */
    @TestConfiguration
    @EnableConfigurationProperties(JevokitApiProperties.class)
    static class SpikeUploadWebConfig {

        @Bean
        IPreventReplayAttack spikePreventReplayAttack() {
            return new DefaultPreventReplayAttack();
        }

        @Bean
        JSecureApiUtil spikeJSecureApiUtil(JevokitApiProperties properties) {
            return new JSecureApiUtil(properties);
        }

        @Bean
        JSecureUploadVerifier spikeUploadVerifier(
                ObjectProvider<JSecureUploadFileRefChecker> refCheckerProvider) {
            return new JSecureUploadVerifier(refCheckerProvider);
        }

        @Bean
        RecordingDecryptAdvice spikeRecordingDecryptAdvice(JevokitApiProperties properties,
                                                           IPreventReplayAttack iPreventReplayAttack,
                                                           JSecureUploadVerifier uploadVerifier) {
            return new RecordingDecryptAdvice(properties, iPreventReplayAttack, uploadVerifier);
        }
    }
}
