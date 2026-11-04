package io.github.jevonsnotes.jevokit.api.client;

import jakarta.validation.Valid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import io.github.jevonsnotes.jevokit.api.annotation.JSecureApi;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiConfiguration;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDetailDto;
import io.github.jevonsnotes.jevokit.api.dto.FetchMode;
import io.github.jevonsnotes.jevokit.api.upload.JSecureUploadUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JSecureClient 默认载体（hutool）真实容器验收：RANDOM_PORT 起嵌入式 Tomcat，
 * 空参 execute() 经真实 HTTP 线路往返——hutool 实发报文由真实容器解析
 * （multipart 边界/CRLF、meta part 按 Content-Type 路由消息转换器、files 按 filename 对齐摘要清单），
 * 协议链（头签名/防重放/解密/验签/上传校验）全通过才算验收。
 *
 * <p>与 {@link JSecureClientTest} 的分工：彼处用 JDK HttpServer 桩断言报文形态
 * （meta 无 filename + application/json、files filename=fileId）+ MockMvc 验证服务端管线；
 * 本类补上"真实容器的 multipart 解析器接受 hutool 报文"这最后一环。
 *
 * <p>装配说明：库模块无 @SpringBootApplication，用显式 classes= 指定的最小
 * {@code @Configuration}（仅 web 自动装配 + JevokitApiConfiguration + 被测端点），
 * 不引入 jasypt 等其他 starter（其静态单例锁不支持多测试上下文）；
 * 注意不可标 @SpringBootConfiguration——否则会被同包 @WebMvcTest 切片的锚点向上搜索命中，
 * 把本类的控制器混入其他切片上下文（Ambiguous mapping）。
 */
@SpringBootTest(classes = JSecureClientDefaultCarrierTest.MinimalServerApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        // jevokit.crypto 启动检查（JevokitCoreConfiguration）：演示密钥与 sample application.yml 同源
        "jevokit.crypto.rsa-public-key=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtaljp48OS0mtwPdhmtbP2zBwsC/oolDx2wmYMrs8shSsyVgd1lempOdm12wSP4e/uKsptbZEuX4q950Tg7x+fyhTtzyyavr4Q4ET2/XisTO+EAp/y59SWy7bjDAJU2wB/DZZZ3L7sbiSCK8383vM/EhgczYz+kX4okjqymR4st3TY3OLZ5y826H/R7emvVNy8MjrvZHREcHzoXnTKWvfcpF10CcbJndIh0tiTtD3tdH0v0kOSmN/qhVBcrL5TOlHLqq1V6aSW9xI1LBNouGlw9pFB4RsOQoJsOH1sygFGy8vCLxvwYNroQZNj//VQhTSVQNg1+Ss1YW5TwrUnL+/EQIDAQAB",
        "jevokit.crypto.rsa-private-key=MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC1qWOnjw5LSa3A92Ga1s/bMHCwL+iiUPHbCZgyuzyyFKzJWB3WV6ak52bXbBI/h7+4qym1tkS5fir3nRODvH5/KFO3PLJq+vhDgRPb9eKxM74QCn/Ln1JbLtuMMAlTbAH8NllncvuxuJIIrzfze8z8SGBzNjP6RfiiSOrKZHiy3dNjc4tnnLzbof9Ht6a9U3LwyOu9kdERwfOhedMpa99ykXXQJxsmd0iHS2JO0Pe10fS/SQ5KY3+qFUFysvlM6UcuqrVXppJb3EjUsE2i4aXD2kUHhGw5Cgmw4fWzKAUbLy8IvG/Bg2uhBk2P/9VCFNJVA2DX5KzVhblPCtScv78RAgMBAAECggEANKby5lbVSscBIGPTG2DVf+8rSjWolJsnblf6kAGq1uadKgJrK3veJf+X1CpTo6xBylF51lwM0UKbS4dwZ4dTlqFmmj5ybAVvvYkE64T5/mhD1zxjy+3SnfEX2yr1GQO+E47hHNW708fuM6wtaFnikOCda+cy64AqT0cZHi4+uYCKxGL9PtYzeNGwHygxJV3XaNBD48IzHUnIstWYEWFmqSPbknRxXhGJnVoEw9JygRngaKoRchairX/FJcwgxcaWwUQMZaLALW5xsB9OIUvsOA5iA+PzTIy+hjgiaIiIvUV2yF16dkkzz9x3fdHYGQer+ezNbXKLd2/lSmku/R7V3QKBgQDbW5949LAdXLwNqKg244wCafVIUyVwNT9yNWkuGZ5B69mfBmo+0Rx7CCPbbbyMF6hcWMwEO8qQPqjZG7w+EJ4T5TYApH5LM4s5NZVeXUMZn+0DZgTsCzorsib0VNbGVs6JCFrrsBv1U/bUhy2Qyt8fUCgb07yYYcEVuk5sqqpjQwKBgQDUAcRV6xTO10edNyxvktk7hn6BtXafbfiNhC9xCjsTrIBUhsF1dG85mVvsvgXLDmHbGvgVvVsGI/Ka6X99mv5OOtlmF2YkFlfWpUci9mZCcnbG7jKLDF/X6tRHuVtZdGMucoCqAnLuDw+5XvMnKvjgYBLKgT02ngOs+HKV+tGtGwKBgHf5EKuTCI/GnIPL7tS2d2kKksDP2PYZ6W2JkNFJ6aicWhYD6DAbJryToyjsR+dHOSno1wyptwxVePvlk3tOk2bGQlbvoLN4qXjMubk2wPBO686kCaUgLtyfP3b6v80SxTtlenTViTUQizuKA33ht07lBErT755YOMatcx8z6KUJAoGBAJYVhiN432sqdhUzKO73m3eLV+WFTCvDU1EDwf4ZSwc7eEHef/ghbFfvXGc1gq5kSRwudHAXc+fd7PKD2HBYGC3TdXHToMhtVNOsf7adjnj9xV5A5GERaoxVPuBjtOqX/Vq1TqaMQ0aKPQxyzlntDvDwsnzPj0EqDdoWrC/Gt0b/AoGAfhEIqXzhubeOtsYHOswkSI20PkhXSlNY9KARUiN5xOyqv7Z8IM2TXzSvPQFiMoPVJqn4/2sWGQh/UC23rXbhCsfW7NGloJhlKM76BptqFsn3EV6w4PN2Au2UtVa17qhM8g2TbOZLBGxHTxecWTwuHu+w70bsgxOwvzTxkYcYk+g=",
        "jevokit.crypto.aes-key=DemoAesKey123456",
        "jevokit.crypto.sm4-key=DemoSm4Key123456",
        "jevokit.api.client[0].id=carrier-e2e-client",
        "jevokit.api.client[0].secret-key=0123456789abcdef",
        // 演示 SM2 密钥对（与 sample application.yml 同源，仅用于测试）
        "jevokit.api.client[0].private-key=MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk5B6bcSXNwvtfd6nufsDHEPOu8N2hHKJMBW5blZP2eSgCgYIKoEcz1UBgi2hRANCAASxO58/zvEseRlMZtRlojzn7m4jAaZPTDSOalPCSk3nnGW0GmEu2ZhfWDNH+Nq8wA6BLodb2gwI9NF+F1Mp3p9R",
        "jevokit.api.client[0].public-key=MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEsTufP87xLHkZTGbUZaI85+5uIwGmT0w0jmpTwkpN55xltBphLtmYX1gzR/javMAOgS6HW9oMCPTRfhdTKd6fUQ==",
        "jevokit.api.client[0].verify=true",
        "jevokit.api.client[0].prevent-replay=false",
        "jevokit.api.client[0].decrypt=true",
        "jevokit.api.client[0].encrypt=true",
})
@DisplayName("JSecureClient 默认载体真实容器往返")
class JSecureClientDefaultCarrierTest {

    private static final String CLIENT_ID = "carrier-e2e-client";
    private static final String TRANS_TYPE = "TN-CARRIER-E2E";

    @LocalServerPort
    int port;

    @Test
    @DisplayName("JSON 通道默认载体：hutool 实发 → 真容器协议链 → 响应解密还原，code=0")
    void jsonChannelDefaultCarrierRoundTripsRealContainer() {
        JSecureClientTest.EchoPayload payload = new JSecureClientTest.EchoPayload();
        payload.setName("real-container");

        JSecureResponse<JSecureClientTest.EchoPayload> response = JSecureClient.of(CLIENT_ID)
                .post(baseUrl() + "/secure/echo", payload, JSecureClientTest.EchoPayload.class)
                .transNo(UUID.randomUUID().toString())
                .transType(TRANS_TYPE)
                .execute();

        assertThat(response.getCode()).as("默认载体真实线路应走通全管线").isEqualTo("0");
        assertThat(response.getTarget()).isNotNull();
        assertThat(response.getTarget().getName()).isEqualTo("real-container");
    }

    @Test
    @DisplayName("上传通道默认载体：hutool multipart 真容器受理（meta 路由 + files 对齐 + 摘要比对），code=0")
    void uploadChannelDefaultCarrierRoundTripsRealContainer() {
        JSecureResponse<String> response = JSecureClient.of(CLIENT_ID)
                .upload(baseUrl() + "/secure/upload", Map.of("orderNo", "SO-2026-001"), String.class)
                .transNo(UUID.randomUUID().toString())
                .transType(TRANS_TYPE)
                .add("invoice-bytes".getBytes(StandardCharsets.UTF_8), "file-1", "invoice", "发票.pdf")
                .add("id-card-bytes".getBytes(StandardCharsets.UTF_8), "file-2", "id_card", "身份证.jpg")
                .execute();

        assertThat(response.getCode())
                .as("hutool multipart 形态应被真实容器按协议受理（meta 路由消息转换器、files 对齐摘要清单）")
                .isEqualTo("0");
        assertThat(response.getTarget()).isEqualTo("accepted:2/2");
    }

    @Test
    @DisplayName("上传接收闭环：Controller 绑定的文件内容+filename 与发送侧逐字节一致（校验源=绑定源）")
    void uploadBoundFilesMatchSentBytesEndToEnd() {
        // 发送内容含中文与重复前缀（防"内容截断/串包靠前缀巧合匹配"），fileId 顺序与 meta 顺序一致但断言不依赖顺序
        byte[] invoice = "发票内容-字节级闭环-INV-2026".getBytes(StandardCharsets.UTF_8);
        byte[] idCard = "身份证内容-字节级闭环-ID-2026".getBytes(StandardCharsets.UTF_8);
        JSecureResponse<Map> rawResponse = JSecureClient.of(CLIENT_ID)
                .upload(baseUrl() + "/secure/upload-echo", Map.of("orderNo", "SO-2026-002"), Map.class)
                .transNo(UUID.randomUUID().toString())
                .transType(TRANS_TYPE)
                .add(invoice, "file-inv", "invoice", "发票.pdf")
                .add(idCard, "file-idc", "id_card", "身份证.jpg")
                .execute();

        assertThat(rawResponse.getCode()).as("回读端点应受理").isEqualTo("0");
        @SuppressWarnings("unchecked") Map<String, String> received = rawResponse.getTarget();
        assertThat(received).as("Controller 应收到两个文件，filename（即 fileId）为键")
                .containsOnlyKeys("file-inv", "file-idc");
        assertThat(received.get("file-inv")).isEqualTo(new String(invoice, StandardCharsets.UTF_8));
        assertThat(received.get("file-idc")).isEqualTo(new String(idCard, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("POJO 全链路：真实业务对象入（B+扩展F）→ 真实 VO 出（R），两端解析逐字段核对")
    void uploadPojoRoundTripRealContainer() throws IOException {
        byte[] invoice = "真实对象发票内容-INV-777".getBytes(StandardCharsets.UTF_8);
        // B：真实业务对象（中文/数值字段）；F：扩展文件条目（带 invoiceNo 个性字段），经 entry() 全自定
        OrderBizData biz = new OrderBizData();
        biz.setOrderNo("PO-2026-0331");
        biz.setAmount(88.50);
        biz.setRemark("真实对象闭环");

        InvoiceFileDto invoiceEntry = new InvoiceFileDto();
        invoiceEntry.setFileId("inv-777");
        invoiceEntry.setFileType("invoice");
        invoiceEntry.setFileName("专票.pdf");
        invoiceEntry.setSize((long) invoice.length);
        invoiceEntry.setDigest(JSecureUploadUtil.digestHex(invoice, "sha256"));
        invoiceEntry.setFetchMode(FetchMode.push);
        invoiceEntry.setInvoiceNo("044031900111");

        JSecureResponse<UploadReceiptVo> response = JSecureClient.of(CLIENT_ID)
                .upload(baseUrl() + "/secure/upload-pojo", biz, UploadReceiptVo.class)
                .transNo(UUID.randomUUID().toString())
                .transType(TRANS_TYPE)
                .entry(invoiceEntry, invoice)                       // F 扩展条目（含个性字段）
                .add("合同附件字节".getBytes(StandardCharsets.UTF_8), "annex-1", "contract", "合同.pdf")
                .execute();

        assertThat(response.getCode()).as("POJO 端点应受理").isEqualTo("0");
        UploadReceiptVo receipt = response.getTarget();             // R：真实 VO，客户端反序列化
        assertThat(receipt).isNotNull();
        assertThat(receipt.getOrderNo()).as("服务端应正确解析 B 的业务字段并回显")
                .isEqualTo("PO-2026-0331");
        assertThat(receipt.getAmount()).isEqualTo(88.50);
        assertThat(receipt.getFileCount()).isEqualTo(2);
        assertThat(receipt.getInvoiceNos()).as("服务端应解析 F 扩展字段（invoiceNo）")
                .containsExactly("044031900111");
        assertThat(receipt.getFileContents()).as("服务端绑定文件内容应逐字节一致")
                .containsEntry("inv-777", new String(invoice, StandardCharsets.UTF_8))
                .containsEntry("annex-1", "合同附件字节");
    }

    /** POJO 全链路用例的业务入参对象（B 泛型，中文+数值字段）。 */
    public static class OrderBizData {
        private String orderNo;
        private Double amount;
        private String remark;

        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    /** POJO 全链路用例的扩展文件条目（F 泛型，继承基类加个性字段 invoiceNo）。 */
    public static class InvoiceFileDto extends JSecureUploadFileDetailDto {
        private String invoiceNo;

        public String getInvoiceNo() { return invoiceNo; }
        public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
    }

    /** POJO 全链路用例的响应 VO（R 泛型：回显字段+列表+Map，验证客户端真实对象解析）。 */
    public static class UploadReceiptVo {
        private String orderNo;
        private Double amount;
        private Integer fileCount;
        private java.util.List<String> invoiceNos;
        private Map<String, String> fileContents;

        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
        public Integer getFileCount() { return fileCount; }
        public void setFileCount(Integer fileCount) { this.fileCount = fileCount; }
        public java.util.List<String> getInvoiceNos() { return invoiceNos; }
        public void setInvoiceNos(java.util.List<String> invoiceNos) { this.invoiceNos = invoiceNos; }
        public Map<String, String> getFileContents() { return fileContents; }
        public void setFileContents(Map<String, String> fileContents) { this.fileContents = fileContents; }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    /** 最小服务端装配：web 自动装配（嵌入式 Tomcat + MVC）+ 协议配置 + 被测端点。 */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({RefreshAutoConfiguration.class, JevokitApiConfiguration.class, CarrierController.class})
    static class MinimalServerApp {
    }

    /** 被测端点：JSON 通道 /secure/echo + 上传通道 /secure/upload。 */
    @RestController
    static class CarrierController {

        @JSecureApi(transType = TRANS_TYPE)
        @PostMapping("/secure/echo")
        public JSecureResponse<JSecureClientTest.EchoPayload> echo(
                @RequestBody @Valid JSecureRequest<JSecureClientTest.EchoPayload> request) {
            JSecureClientTest.EchoPayload res = new JSecureClientTest.EchoPayload();
            res.setName(request.getTarget().getName());
            return JSecureResponse.ok(res);
        }

        @JSecureApi(transType = TRANS_TYPE)
        @PostMapping("/secure/upload")
        public JSecureResponse<String> upload(
                @RequestPart("meta") @Valid JSecureRequest<JSecureUploadDto<Map<String, Object>, JSecureUploadFileDetailDto>> meta,
                @RequestPart(value = "files", required = false) List<MultipartFile> files) {
            int declared = meta.getTarget().getFileDetails() == null ? 0 : meta.getTarget().getFileDetails().size();
            int bound = files == null ? 0 : files.size();
            return JSecureResponse.ok("accepted:" + declared + "/" + bound);
        }

        /**
         * 回读端点（接收闭环专用）：把 Controller 实际绑定到的每个文件按 filename（=fileId）回显内容，
         * 客户端逐字节核对——证明"校验时读的 parts"与"参数绑定给的 MultipartFile"同源同内容。
         */
        @JSecureApi(transType = TRANS_TYPE)
        @PostMapping("/secure/upload-echo")
        public JSecureResponse<Map<String, String>> uploadEcho(
                @RequestPart("meta") @Valid JSecureRequest<JSecureUploadDto<Map<String, Object>, JSecureUploadFileDetailDto>> meta,
                @RequestPart(value = "files", required = false) List<MultipartFile> files) throws IOException {
            Map<String, String> echo = new LinkedHashMap<>();
            if (files != null) {
                for (MultipartFile file : files) {
                    echo.put(file.getOriginalFilename(),
                            new String(file.getBytes(), StandardCharsets.UTF_8));
                }
            }
            return JSecureResponse.ok(echo);
        }

        /**
         * POJO 全链路端点：真实业务对象入（B=OrderBizData、F=InvoiceFileDto 扩展条目）→
         * 真实 VO 出（回显业务字段 + 扩展字段清单 + 文件内容回读），验证两端真实对象解析。
         */
        @JSecureApi(transType = TRANS_TYPE)
        @PostMapping("/secure/upload-pojo")
        public JSecureResponse<UploadReceiptVo> uploadPojo(
                @RequestPart("meta") @Valid JSecureRequest<JSecureUploadDto<OrderBizData, InvoiceFileDto>> meta,
                @RequestPart(value = "files", required = false) List<MultipartFile> files) throws IOException {
            JSecureUploadDto<OrderBizData, InvoiceFileDto> upload = meta.getTarget();
            UploadReceiptVo vo = new UploadReceiptVo();
            vo.setOrderNo(upload.getBusinessData().getOrderNo());          // B 解析
            vo.setAmount(upload.getBusinessData().getAmount());
            vo.setFileCount(files == null ? 0 : files.size());
            vo.setInvoiceNos(upload.getFileDetails().stream()
                    .map(InvoiceFileDto::getInvoiceNo)                      // F 扩展字段解析
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toList()));
            Map<String, String> contents = new LinkedHashMap<>();
            if (files != null) {
                for (MultipartFile file : files) {
                    contents.put(file.getOriginalFilename(),
                            new String(file.getBytes(), StandardCharsets.UTF_8));
                }
            }
            vo.setFileContents(contents);
            return JSecureResponse.ok(vo);
        }
    }
}
