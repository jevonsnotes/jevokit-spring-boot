package io.github.jevonsnotes.jevokit.api.selfimplement;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JSecure 协议"不依赖 jevokit"自实现参考（自包含，复制即用）。
 *
 * <p>定位：合作方项目不引 jevokit jar、用自有国密设施对接 JSecure 服务端时的最小完整样例——
 * 仅依赖 JDK 17+、BouncyCastle（国密算法）与 fastjson2（JSON，与 jevokit 全线同款），不含任何
 * jevokit/hutool 类。各方法的字节级协议口径（IV 前置、签名算法、MAC 拼接、信封字段、multipart 形态）
 * 与 jevokit-api 服务端实现逐项对齐，已在本模块 {@code JSecureProtocolDemoTest}
 * 对真实容器服务端实测通过（JSON 通道 + 上传通道全链路），协议升级时该测试同步回归。
 *
 * <p>依赖（国密部分 JDK 原生不支持 SM2/SM4，零依赖做不到；JSON 库可任选——服务端解析只认
 * 信封字段名，容忍字段顺序与多余字段，对接方用 Jackson/Gson 亦可，参考实现选 fastjson2
 * 系与 jevokit 同款、降低对照成本）：
 * <pre>{@code
 * <dependency>
 *     <groupId>org.bouncycastle</groupId>
 *     <artifactId>bcprov-jdk18on</artifactId>
 *     <version>1.86</version>     <!-- 对齐 jevokit BOM 的 bouncycastle 版本 -->
 * </dependency>
 * <dependency>
 *     <groupId>com.alibaba.fastjson2</groupId>
 *     <artifactId>fastjson2</artifactId>
 *     <version>2.0.53</version>   <!-- 对齐 jevokit BOM 的 fastjson2 版本 -->
 * </dependency>
 * }</pre>
 *
 * <p>协议规格速查（与服务端 {@code jevokit.api.client[]} 成对登记的终端配置）：
 * <ul>
 *   <li>SM4 数据加密：SM4/CBC/PKCS7Padding（JCE 名 PKCS5Padding，16 字节块两者等价），
 *       密钥 = secret-key-sm4（缺省回落 secret-key）的 ASCII 字节（16 字符），
 *       IV = 每次随机 16 字节并<strong>前置于密文</strong>，整体 Base64；明文按 UTF-8 编码。</li>
 *   <li>SM2 报文签名：SM3withSM2（默认 userId 1234567812345678），DER 编码（ASN.1 SEQUENCE{r,s}），
 *       Base64 输出；私钥为 PKCS#8/Base64，公钥为 X.509/Base64。
 *       签名对象 = 业务明文 JSON 字符串的字节（UTF-8，见类注释"字符集口径"）。</li>
 *   <li>请求头签名（MAC）：hex 小写（MD5 或 SHA-256），摘要原文 = timestamp + secretKey + nonce
 *       （无分隔符、无大小写变换，顺序固定）。</li>
 *   <li>协议头（默认前缀 x-api，实际名 = 前缀 + - + 名）：x-api-client-id / x-api-timestamp（秒级）/
 *       x-api-nonce（随机码，任一每次不同的字符串，默认 UUID）/ x-api-signature。</li>
 *   <li>请求信封：{"transNo","transType","signature","data"}，data 为 SM4 密文，view/target 不上送；
 *       服务端解析容忍字段顺序差异与多余字段（transType 缺省或 null 均可），第三方 JSON 库任选。</li>
 *   <li>响应信封：{"transNo","transType","code","message","signature","data"}，code=0 成功；
 *       data 解密得业务明文 JSON（view），对其验 SM2 签名。</li>
 *   <li>上传通道：multipart/form-data，meta part（name=meta、Content-Type=application/json、
 *       不带 filename）装上述信封（data 为 {"businessData":…,"files":[…]} 的密文），
 *       文件 part（name=files、filename=fileId、application/octet-stream）明文传输，
 *       摘要清单按 sha256（hex 小写）声明，协议头挂根请求。</li>
 * </ul>
 *
 * <p>字符集口径（重要）：SM2 签名/验签原文取字节的口径由服务端 {@code jevokit.crypto.charset}
 * 配置决定（3.5.3.0 起配置化，**默认 UTF-8**，不再跟随 JVM 默认字符集）。本参考实现显式按
 * UTF-8 签名/验签，与服务端默认配置互通；若服务端为对接 GBK 存量系统而配置了
 * {@code charset: GBK}，须把本类 sm2Sign/sm2Verify 的明文取字节改为同一字符集
 * （验签互通要求两端口径一致；纯 ASCII 报文——含全部 Base64 密文/签名——不受影响）。
 */
public class JSecureProtocolDemo {

    static {
        // 注册国密提供者（SM2/SM4/SM3 算法由 BouncyCastle 提供，JDK 原生不含）
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /** IV 与 boundary 随机源。 */
    private static final SecureRandom RANDOM = new SecureRandom();

    // ===================== 终端配置（与服务端 jevokit.api.client[] 成对登记） =====================

    /** 终端识别码（服务端 jevokit.api.client[].id）。 */
    private final String clientId;
    /** 头签名密钥（jevokit.api.client[].secret-key，参与 MAC 拼接）。 */
    private final String secretKey;
    /** SM4 密钥（16 字符 ASCII；服务端 secret-key-sm4，缺省回落 secret-key）。 */
    private final byte[] sm4Key;
    /** SM2 私钥（PKCS#8 / Base64）：请求报文签名用。 */
    private final String privateKeyBase64;
    /** SM2 公钥（X.509 / Base64）：响应报文验签用（与服务端 private-key 同对）。 */
    private final String publicKeyBase64;
    /** 协议头前缀（服务端 jevokit.api.client[].header.prefix，默认 x-api）。 */
    private final String headerPrefix;
    /** 头签名算法（服务端 jevokit.api.client[].header.sign-alg：md5 / sha256）。 */
    private final String headerSignAlg;

    /** JDK 内置 HttpClient（生产可按需换 OkHttp 等，协议组装不依赖具体客户端）。 */
    private final HttpClient http = HttpClient.newHttpClient();

    public JSecureProtocolDemo(String clientId, String secretKey, String secretKeySm4,
                               String privateKeyBase64, String publicKeyBase64,
                               String headerPrefix, String headerSignAlg) {
        this.clientId = clientId;
        this.secretKey = secretKey;
        // 与服务端口径一致：SM4 密钥为密钥字符串的字符字节（16 字符 ASCII，UTF-8 与平台默认无差异）
        String sm4KeyStr = (secretKeySm4 == null || secretKeySm4.isEmpty()) ? secretKey : secretKeySm4;
        this.sm4Key = sm4KeyStr.getBytes(StandardCharsets.UTF_8);
        this.privateKeyBase64 = privateKeyBase64;
        this.publicKeyBase64 = publicKeyBase64;
        this.headerPrefix = headerPrefix;
        this.headerSignAlg = headerSignAlg;
    }

    /** 常用便捷构造：默认头前缀 x-api、头签名算法 md5（与服务端缺省一致）。 */
    public JSecureProtocolDemo(String clientId, String secretKey, String secretKeySm4,
                               String privateKeyBase64, String publicKeyBase64) {
        this(clientId, secretKey, secretKeySm4, privateKeyBase64, publicKeyBase64, "x-api", "md5");
    }

    // ===================== 协议原语（与 jevokit 服务端逐字节一致） =====================

    /**
     * SM4-CBC 加密（协议 data 字段）：随机 16 字节 IV 前置 + PKCS#7 填充，整体 Base64。
     *
     * @param plaintext 业务明文（JSON 字符串）
     * @return 密文（Base64(iv + 密文)）
     */
    public String sm4CbcEncrypt(String plaintext) {
        try {
            byte[] iv = new byte[16];
            RANDOM.nextBytes(iv);
            // PKCS5Padding 在 16 字节块的 SM4 上即 PKCS#7 语义（BC 两种名字等价），与服务端 hutool 同参
            Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS5Padding", BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sm4Key, "SM4"), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("SM4 加密失败", e);
        }
    }

    /**
     * SM4-CBC 解密（协议响应 data 字段）：前 16 字节为 IV，余量为密文。
     *
     * @param cipherBase64 Base64(iv + 密文)
     * @return 业务明文（JSON 字符串）
     */
    public String sm4CbcDecrypt(String cipherBase64) {
        try {
            byte[] all = Base64.getDecoder().decode(cipherBase64);
            byte[] iv = new byte[16];
            byte[] content = new byte[all.length - 16];
            System.arraycopy(all, 0, iv, 0, 16);
            System.arraycopy(all, 16, content, 0, content.length);
            Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS5Padding", BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(sm4Key, "SM4"), new IvParameterSpec(iv));
            return new String(cipher.doFinal(content), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("SM4 解密失败", e);
        }
    }

    /**
     * SM2 签名（协议 signature 字段）：SM3withSM2 + 默认 userId（1234567812345678），DER 编码，Base64 输出。
     *
     * @param plaintext 签名原文（业务明文 JSON 字符串，UTF-8 字节）
     * @return 签名（Base64 的 DER 序列化）
     */
    public String sm2Sign(String plaintext) {
        try {
            Signature signature = Signature.getInstance("SM3withSM2", BouncyCastleProvider.PROVIDER_NAME);
            signature.initSign(loadPrivateKey());
            signature.update(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("SM2 签名失败", e);
        }
    }

    /**
     * SM2 验签（响应报文）：对解密出的明文（view）验服务端签名。
     *
     * @param plaintext       验签原文（响应解密出的业务明文）
     * @param signatureBase64 响应信封 signature 字段
     * @return true 验签通过
     */
    public boolean sm2Verify(String plaintext, String signatureBase64) {
        try {
            Signature signature = Signature.getInstance("SM3withSM2", BouncyCastleProvider.PROVIDER_NAME);
            signature.initVerify(loadPublicKey());
            signature.update(plaintext.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            throw new IllegalStateException("SM2 验签失败", e);
        }
    }

    /**
     * 请求头签名（MAC）：hex 小写摘要，原文 = timestamp + secretKey + nonce（顺序固定、无分隔符）。
     *
     * @param timestamp 秒级时间戳字符串
     * @param nonce     随机码（每次请求唯一）
     * @return 摘要（hex 小写；算法按终端 header.sign-alg，md5 或 sha256）
     */
    public String headerSign(String timestamp, String nonce) {
        String plaintext = timestamp + secretKey + nonce;
        try {
            String algorithm = "sha256".equalsIgnoreCase(headerSignAlg)
                    ? "SHA-256" : "MD5";
            return hex(MessageDigest.getInstance(algorithm).digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("请求头签名计算失败", e);
        }
    }

    /**
     * 组装协议头四件套（挂根请求）：client-id / timestamp（秒级）/ nonce / signature。
     */
    public Map<String, String> buildProtocolHeaders() {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = UUID.randomUUID().toString();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(headerName("client-id"), clientId);
        headers.put(headerName("timestamp"), timestamp);
        headers.put(headerName("nonce"), nonce);
        headers.put(headerName("signature"), headerSign(timestamp, nonce));
        return headers;
    }

    /** 头名拼接：前缀已以 - 结尾直接拼，否则补 -（与服务端 rebuildHeader 同口径）。 */
    private String headerName(String name) {
        return headerPrefix.endsWith("-") ? headerPrefix + name : headerPrefix + "-" + name;
    }

    /** SM2 私钥对象（PKCS#8/Base64 → BCECPrivateKey；按需缓存由调用方自理，此处演示直解析）。 */
    private PrivateKey loadPrivateKey() throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64)));
    }

    /** SM2 公钥对象（X.509/Base64 → BCECPublicKey）。 */
    private PublicKey loadPublicKey() throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        return keyFactory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
    }

    // ===================== JSON 通道（@RequestBody 信封端点） =====================

    /**
     * JSON 通道一次完整请求：业务 JSON → SM2 签名 + SM4 加密入信封 → 协议头 → POST →
     * 响应解密验签，返回业务明文 JSON（view）。code != 0 或验签失败抛异常。
     *
     * @param url          服务端地址（@JSecureApi + @RequestBody JSecureRequest 端点）
     * @param businessJson 业务数据 JSON 字符串（字段名与服务端 target 类型一致，JSON 库任选）
     * @param transNo      交易号（服务端 @NotBlank 必填，可传 UUID）
     * @param transType    交易类型（服务端 @JSecureApi 声明了限定则必填，否则可 null）
     * @return 已解密验签的业务明文 JSON
     */
    public String postJson(String url, String businessJson, String transNo, String transType) {
        String envelope = buildEnvelope(transNo, transType, sm2Sign(businessJson), sm4CbcEncrypt(businessJson));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json");
        buildProtocolHeaders().forEach(request::header);
        return exchange(url, request);
    }

    // ===================== 上传通道（multipart：meta part + files part） =====================

    /** 上传文件条目（push 直传）：fileId 即文件 part 的 filename（对齐键）。 */
    public static class UploadFile {

        /** 请求内唯一标识：仅允许字母、数字与 . _ -（1-128 字符），即文件 part 的 filename。 */
        public final String fileId;
        /** 业务类型（invoice/id_card 等，服务端 format 白名单按它配置）。 */
        public final String fileType;
        /** 展示用原始文件名（仅展示，服务端永不落盘；扩展名用于推断 format）。 */
        public final String fileName;
        /** 文件格式（小写扩展名，如 pdf；无扩展名传空串，服务端配了白名单则会被拒）。 */
        public final String format;
        /** 文件内容字节。 */
        public final byte[] bytes;

        public UploadFile(String fileId, String fileType, String fileName, String format, byte[] bytes) {
            this.fileId = fileId;
            this.fileType = fileType;
            this.fileName = fileName;
            this.format = format;
            this.bytes = bytes;
        }
    }

    /**
     * 上传通道一次完整请求（push 直传）：业务数据 + 文件摘要清单（sha256）整体签名加密为
     * meta part 信封，文件逐个作 files part（filename = fileId），协议头挂根请求；
     * 响应解密验签，返回业务明文 JSON。
     *
     * @param url          服务端地址（@JSecureApi + @RequestPart("meta") + @RequestPart("files") 端点）
     * @param businessJson 业务数据 JSON 字符串
     * @param transNo      交易号
     * @param transType    交易类型（服务端声明了限定则必填）
     * @param files        push 文件列表（可为空列表：纯 pull/refer 场景自行扩展清单条目）
     * @return 已解密验签的业务明文 JSON
     */
    public String uploadFiles(String url, String businessJson, String transNo, String transType,
                              List<UploadFile> files) {
        // 1. 文件摘要清单（sha256 hex 小写）+ 业务体组装（与服务端 JSecureUploadDto 字段一致）
        List<Object> entries = new ArrayList<>(files.size());
        for (UploadFile file : files) {
            JSONObject entry = new JSONObject();
            entry.put("fileId", file.fileId);
            entry.put("fileType", file.fileType);
            entry.put("fileName", file.fileName);
            entry.put("format", file.format);
            entry.put("size", (long) file.bytes.length);
            entry.put("digest", sha256Hex(file.bytes));
            entry.put("digestAlg", "sha256");
            entry.put("fetchMode", "push");
            entries.add(entry);
        }
        JSONObject upload = new JSONObject();
        upload.put("businessData", JSON.parse(businessJson));
        upload.put("files", entries);
        String uploadJson = upload.toJSONString();
        // 2. 信封签名加密（与 JSON 通道同配方），作为 meta part 内容
        String metaJson = buildEnvelope(transNo, transType, sm2Sign(uploadJson), sm4CbcEncrypt(uploadJson));
        // 3. 手工组装 multipart（JDK HttpClient 无内置 multipart 支持）
        try {
            String boundary = "----JSecureDemo" + UUID.randomUUID().toString().replace("-", "");
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            // meta part：不带 filename（服务端按"非文件 part"识别），Content-Type 声明 application/json
            writePart(body, boundary, "meta", null, "application/json",
                    metaJson.getBytes(StandardCharsets.UTF_8));
            for (UploadFile file : files) {
                // 文件 part：part 名恒为 files，filename = fileId（服务端按它对齐摘要清单）
                writePart(body, boundary, "files", file.fileId, "application/octet-stream", file.bytes);
            }
            body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary);
            buildProtocolHeaders().forEach(request::header);
            return exchange(url, request);
        } catch (IOException e) {
            throw new IllegalStateException("multipart 组装失败", e);
        }
    }

    /** 写一个 multipart part（标准 CRLF 行分隔；boundary 无特殊要求，任意不与内容冲突的串均可）。 */
    private static void writePart(ByteArrayOutputStream out, String boundary, String name,
                                  String filename, String contentType, byte[] content) throws IOException {
        String disposition = filename == null
                ? "form-data; name=\"" + name + "\""
                : "form-data; name=\"" + name + "\"; filename=\"" + filename + "\"";
        out.write(("--" + boundary + "\r\n"
                + "Content-Disposition: " + disposition + "\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    // ===================== 信封组装与响应统一处理（fastjson2，与 jevokit 同款） =====================

    /**
     * 组装请求信封 JSON（字段名精确：transNo/transType/signature/data）。
     * transType 为 null 时显式写 null——服务端对"显式 null"与"省略字段"（jevokit 客户端 fastjson2
     * 默认省略 null）两种形态均受理，对接方保持任一形态即可。
     */
    static String buildEnvelope(String transNo, String transType, String signature, String data) {
        JSONObject envelope = new JSONObject();
        envelope.put("transNo", transNo);
        envelope.put("transType", transType);
        envelope.put("signature", signature);
        envelope.put("data", data);
        // WriteMapNullValue：null 值显式写 null（保持与参考实现既定 wire 形态一致）
        return JSON.toJSONString(envelope, JSONWriter.Feature.WriteMapNullValue);
    }

    /** 发送并处理响应：非 2xx 抛传输异常；code != 0 抛协议异常；data 解密验签后返回明文。 */
    private String exchange(String url, HttpRequest.Builder request) {
        try {
            HttpResponse<String> response =
                    http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP 状态码 " + response.statusCode() + "，URL：" + url);
            }
            return handleResponse(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("JSecure 请求发送失败：" + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("JSecure 请求被中断：" + url, e);
        }
    }

    /**
     * 响应统一处理：解析信封 → code 判定 → data 解密（view）→ SM2 验签。
     * 无数据体响应（JSecureResponse.ok()）返回 null。
     */
    private String handleResponse(String body) {
        JSONObject envelope = JSON.parseObject(body);
        String code = envelope.getString("code");
        if (!"0".equals(code)) {
            throw new IllegalStateException("协议失败：code=" + code + "，message=" + envelope.getString("message"));
        }
        String data = envelope.getString("data");
        if (data == null || data.isEmpty()) {
            return null;
        }
        String view = sm4CbcDecrypt(data);
        String signature = envelope.getString("signature");
        if (signature != null && !sm2Verify(view, signature)) {
            throw new IllegalStateException("返回结果验签失败");
        }
        return view;
    }

    // ===================== 摘要工具 =====================

    /** sha256 摘要（hex 小写），上传摘要清单用。 */
    static String sha256Hex(byte[] content) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException("摘要计算失败", e);
        }
    }

    /** 字节数组转 hex 小写。 */
    static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    // ===================== 可运行入口 =====================

    /**
     * 演示入口：对已部署的 JSecure 服务端各走一次 JSON 通道与上传通道。
     * 运行前替换 URL 与终端配置为服务端发放值（下方为仓库演示密钥，仅样例形态）；
     * 注意服务端终端的 allow-path / allow-ip / transType 限定须放行本请求
     * （示例端点为 jevokit-sample 的 /secure/test 与 /secure/upload）。
     */
    public static void main(String[] args) {
        // 终端配置：与服务端 jevokit.api.client[] 的登记值成对（示例为 sample 演示终端 client-demo-1）
        JSecureProtocolDemo demo = new JSecureProtocolDemo(
                "client-demo-1",
                "demo-sm4-secret",
                "demo-sm4-secret",
                "MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk5B6bcSXNwvtfd6nufsDHEPOu8N2hHKJMBW5blZP2eSgCgYIKoEcz1UBgi2hRANCAASxO58/zvEseRlMZtRlojzn7m4jAaZPTDSOalPCSk3nnGW0GmEu2ZhfWDNH+Nq8wA6BLodb2gwI9NF+F1Mp3p9R",
                "MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEsTufP87xLHkZTGbUZaI85+5uIwGmT0w0jmpTwkpN55xltBphLtmYX1gzR/javMAOgS6HW9oMCPTRfhdTKd6fUQ==");

        // JSON 通道：业务体 {"name":...}，交易类型须在服务端 @JSecureApi 限定内（sample 为 T030421 或空）
        String view = demo.postJson("http://127.0.0.1:8080/secure/test",
                "{\"name\":\"self-implement\"}", "t-selfimpl-001", "T030421");
        System.out.println("JSON 通道响应明文：" + view);

        // 上传通道：两个 push 文件（fileId 仅字母/数字/._-，fileName 可为中文）
        String uploadView = demo.uploadFiles("http://127.0.0.1:8080/secure/upload",
                "{\"orderNo\":\"SO-SELFIMPL-001\"}", "t-selfimpl-002", "T030422",
                List.of(
                        new UploadFile("inv-1", "invoice", "invoice.pdf", "pdf",
                                "invoice-bytes".getBytes(StandardCharsets.UTF_8)),
                        new UploadFile("id-1", "id_card", "idcard.jpg", "jpg",
                                "id-card-bytes".getBytes(StandardCharsets.UTF_8))));
        System.out.println("上传通道响应明文：" + uploadView);
    }
}
