package io.github.jevonsnotes.jevokit.sample.tools;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.RandomUtil;
import io.github.jevonsnotes.jevokit.api.client.JSecureClient;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadRequest;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 一次性对比基准：同文件分别走 接口二 upload（multipart，文件明文）与 接口一 JSON 通道
 * （文件 base64 塞进业务字段，随 data 整体 SM4 加密），测真实 HTTP 往返耗时与 wire 体积。
 * 前置：sample 服务在 18080 运行（local profile）。
 */
public final class UploadVsBase64Benchmark {

    private static final String BASE = "http://127.0.0.1:18080";
    private static final String CLIENT_ID = "client-demo-1";
    private static final int ROUNDS = 5;

    private UploadVsBase64Benchmark() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        initClientConfigStandalone();
        for (int kb : new int[]{100, 1024, 5120}) {
            byte[] content = randomBytes(kb * 1024 - 1024);   // 留 1KB 余量：密文比明文多 ~32B（IV+padding），避开 5MB 上限压线
            bench(kb + "KB", content);
        }
    }

    /** 脱离 Spring 跑客户端：手动注入 client-demo-1 终端配置（与 sample application.yml + local 覆盖同源）。 */
    static void initClientConfigStandalone() {
        io.github.jevonsnotes.jevokit.api.config.JevokitApiProperties props =
                new io.github.jevonsnotes.jevokit.api.config.JevokitApiProperties();
        io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties client =
                new io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties();
        client.setId(CLIENT_ID);
        client.setSecretKey("demo-sm4-secret");            // 头签名盐
        client.setSecretKeySm4("DemoSm4Key123456");        // SM4 密钥（local 覆盖值）
        client.setPrivateKey(PostmanValuesKit.SM2_PRIVATE_KEY);
        client.setPublicKey("MFkwEwYHKoZIzj0CAQYIKoEcz1UBgi0DQgAEsTufP87xLHkZTGbUZaI85+5uIwGmT0w0jmpTwkpN55xltBphLtmYX1gzR/javMAOgS6HW9oMCPTRfhdTKd6fUQ==");
        client.setVerify(false);
        client.setPreventReplay(false);
        client.setDecrypt(true);
        client.setEncrypt(true);
        client.setUploadMaxSize(5242880L);                  // 演示终端 5MB 上限
        client.setUploadDigestVerify(true);
        client.setUploadSizeVerify(true);
        props.setClient(new java.util.ArrayList<>(java.util.List.of(client)));
        new io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil(props);   // 构造器注入静态配置
    }

    static void bench(String label, byte[] content) throws Exception {
        // 预热各 1 次（JIT/连接池）
        roundUpload(content, false);
        roundUpload(content, true);
        roundJsonBase64(content);

        long upNanos = 0, encNanos = 0, jsonNanos = 0;
        long upBytes = 0, encBytes = 0, jsonBytes = 0;
        for (int i = 0; i < ROUNDS; i++) {
            Result r1 = roundUpload(content, false);
            upNanos += r1.nanos;
            upBytes = r1.wireBytes;
            Result r2 = roundUpload(content, true);
            encNanos += r2.nanos;
            encBytes = r2.wireBytes;
            Result r3 = roundJsonBase64(content);
            jsonNanos += r3.nanos;
            jsonBytes = r3.wireBytes;
        }
        System.out.printf("%-7s upload明文: %6.1f ms wire≈%7d B | upload加密: %6.1f ms wire≈%7d B (+%.0f%%) | json+base64: %7.1f ms wire≈%8d B%n",
                label,
                upNanos / ROUNDS / 1_000_000.0, upBytes,
                encNanos / ROUNDS / 1_000_000.0, encBytes,
                (encBytes - upBytes) * 100.0 / upBytes,
                jsonNanos / ROUNDS / 1_000_000.0, jsonBytes);
    }

    record Result(long nanos, long wireBytes) {
    }

    /** 接口二：upload multipart（文件明文 part + meta 加密信封）；encrypt=true 时文件 SM4-CBC 加密（场景六）。 */
    static Result roundUpload(byte[] content, boolean encrypt) throws Exception {
        JSecureUploadRequest prepared = JSecureClient.of(CLIENT_ID)
                .upload(BASE + "/secure/upload", java.util.Map.of("orderNo", "BENCH"), String.class)
                .transNo(UUID.randomUUID().toString())
                .transType("T030422")
                .add(content, "bench-file.pdf", "invoice", "bench.pdf", encrypt)
                .prepare().getRequest();
        long wire = prepared.getMetaJson().getBytes(StandardCharsets.UTF_8).length
                + prepared.getFiles().get(0).getContent().length + 512;
        long t0 = System.nanoTime();
        JSecureResponse<String> resp = JSecureClient.of(CLIENT_ID)
                .upload(BASE + "/secure/upload", java.util.Map.of("orderNo", "BENCH"), String.class)
                .transNo(UUID.randomUUID().toString())
                .transType("T030422")
                .add(content, "bench-file.pdf", "invoice", "bench.pdf", encrypt)
                .execute();
        if (!"0".equals(resp.getCode())) {
            throw new IllegalStateException("upload(encrypt=" + encrypt + ") 非 0：" + resp.getMessage());
        }
        return new Result(System.nanoTime() - t0, wire);
    }

    /** 接口一：JSON 通道，文件 base64 塞业务字段（随 data 整体 SM4 加密）。 */
    static Result roundJsonBase64(byte[] content) throws Exception {
        String b64 = Base64.encode(content);
        long t0 = System.nanoTime();
        JSecureResponse<java.util.Map> resp = JSecureClient.of(CLIENT_ID)
                .post(BASE + "/secure/test", java.util.Map.of("name", b64), java.util.Map.class)
                .transNo(UUID.randomUUID().toString())
                .transType("T030421")
                .execute();
        if (!"0".equals(resp.getCode())) {
            throw new IllegalStateException("json 非 0：" + resp.getMessage());
        }
        // wire 体积按信封产物估算：重算一次明文 → SM4 密文 base64（与客户端组包一致）
        String plain = com.alibaba.fastjson2.JSON.toJSONString(java.util.Map.of("name", b64));
        long wire = io.github.jevonsnotes.jevokit.core.util.SecurityUtil
                .sm4cbcEncrypt(plain, "DemoSm4Key123456").getBytes(StandardCharsets.US_ASCII).length + 256;
        return new Result(System.nanoTime() - t0, wire);
    }

    static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        new java.security.SecureRandom().nextBytes(b);
        return b;
    }
}
