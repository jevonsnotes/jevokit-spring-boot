package io.github.jevonsnotes.jevokit.sample.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import io.github.jevonsnotes.jevokit.core.util.SecurityUtil;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Postman 联调取值生成器（测试工具，不随 sample 应用运行）——自包含，直接执行 main 即用。
 *
 * <p>背景：client-demo-1 演示终端 verify=false（不验签，signature 可空）、
 * prevent-replay=false（时间戳/nonce 不校验窗口），Postman 侧只需配好请求头与
 * SM4 加密的 meta/data 即可打样例服务。
 *
 * <p>执行方式（任选其一，均在 sample 模块下）：
 * <ul>
 *   <li>IDE：直接 Run 本类 main（无参或有参均可）；</li>
 *   <li>命令行：{@code mvn -q test-compile} 后
 *       {@code mvn -q exec:java -Dexec.classpathScope=test
 *       -Dexec.mainClass=io.github.jevonsnotes.jevokit.sample.tools.PostmanValuesKit
 *       -Dexec.args="<可选：要上传文件的路径>"}。</li>
 * </ul>
 * 行为（无环境依赖，换机即用）：
 * <ol>
 *   <li>上传文件：有参数用参数路径（可多个）；无参时用/自造演示文件
 *       {@code postman-demo/inv-1.pdf}（不存在则自动创建固定内容）；</li>
 *   <li>输出：完整 Postman 配方（请求头四件套 + 接口一报文 + 接口二 meta 信封 + 组装说明）
 *       写入 {@code postman-demo/postman-values-<时间戳>.txt}（每次执行一个新文件），同时打印到控制台；</li>
 *   <li>结束时打印结果文件的绝对路径。</li>
 * </ol>
 */
public final class PostmanValuesKit {

    /** 演示终端 client-demo-1（密钥与 application.yml 同步：头签名盐/SM4/SM2）。 */
    static final String CLIENT_ID = "client-demo-1";
    static final String SECRET_KEY = "demo-sm4-secret";      // 头签名盐
    static final String SECRET_KEY_SM4 = "DemoSm4Key123456"; // SM4 密钥
    static final String SM2_PRIVATE_KEY =
            "MIGTAgEAMBMGByqGSM49AgEGCCqBHM9VAYItBHkwdwIBAQQgk5B6bcSXNwvtfd6nufsDHEPOu8N2hHKJMBW5blZP2eSgCgYIKoEcz1UBgi2hRANCAASxO58/zvEseRlMZtRlojzn7m4jAaZPTDSOalPCSk3nnGW0GmEu2ZhfWDNH+Nq8wA6BLodb2gwI9NF+F1Mp3p9R";
    static final String BASE = "http://127.0.0.1:18080";

    /** 演示文件（无参时使用；不存在自动造）。 */
    static final Path DEMO_DIR = Paths.get("postman-demo");
    static final Path DEMO_FILE = DEMO_DIR.resolve("inv-1.pdf");
    static final String DEMO_FILE_CONTENT = "DEMO INVOICE content for JSecure upload Postman test. bytes-2026.";

    private PostmanValuesKit() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));

        // 1) 定上传文件：有参用参（可多个），无参用/自造演示文件
        List<Path> uploadFiles = new ArrayList<>();
        if (args.length > 0) {
            for (String a : args) {
                Path p = Paths.get(a);
                if (!Files.isReadable(p)) {
                    throw new IllegalArgumentException("文件不可读：" + p.toAbsolutePath());
                }
                uploadFiles.add(p);
            }
        } else {
            if (!Files.exists(DEMO_FILE)) {
                Files.createDirectories(DEMO_DIR);
                Files.writeString(DEMO_FILE, DEMO_FILE_CONTENT, StandardCharsets.UTF_8);
            }
            uploadFiles.add(DEMO_FILE);
        }

        // 2) 生成配方 → 控制台 + 结果文件（postman-demo/postman-values-<时间戳>.txt，每次一个新文件）
        String recipe = buildRecipe(uploadFiles);
        Path outFile = DEMO_DIR.resolve("postman-values-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
        Files.createDirectories(DEMO_DIR);
        Files.writeString(outFile, recipe, StandardCharsets.UTF_8);

        System.out.print(recipe);
        System.out.println();
        System.out.println("═".repeat(76));
        System.out.println("取值已写入：" + outFile.toAbsolutePath());
        System.out.println("上传文件：" + uploadFiles.stream().map(p -> p.toAbsolutePath().toString())
                .reduce((a, b) -> a + "、" + b).orElse(""));
    }

    /** 组装完整 Postman 配方文本。 */
    static String buildRecipe(List<Path> uploadFiles) {
        StringBuilder out = new StringBuilder();

        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = RandomUtil.randomStringUpper(8);
        String headerSign = SecureUtil.md5(timestamp + SECRET_KEY + nonce).toLowerCase();

        out.append("═".repeat(76)).append('\n');
        out.append("【1】请求头四件套（两个接口通用，Headers 页签逐行添加）\n");
        out.append("     当前终端 ").append(CLIENT_ID)
                .append(" 为 verify=false：signature 不参与校验，值仅供规范展示\n");
        out.append("═".repeat(76)).append('\n');
        out.append("x-api-client-id: ").append(CLIENT_ID).append('\n');
        out.append("x-api-timestamp: ").append(timestamp).append('\n');
        out.append("x-api-nonce: ").append(nonce).append('\n');
        out.append("x-api-signature: ").append(headerSign).append('\n');

        // 接口一：/secure/test（data 明文 {"name":"abc"}）
        out.append('\n');
        out.append("═".repeat(76)).append('\n');
        out.append("【2】POST ").append(BASE).append("/secure/test   （Body → raw → JSON，整段粘贴）\n");
        out.append("═".repeat(76)).append('\n');
        out.append(envelope("T030421", "{\"name\":\"abc\"}")).append('\n');

        // 接口二：/secure/upload（全部 push 直传，fileId = 文件名）
        List<Map<String, Object>> fileDetails = new ArrayList<>();
        for (Path p : uploadFiles) {
            byte[] bytes = FileUtil.readBytes(p.toFile());
            String fileName = p.getFileName().toString();
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("fileId", fileName);
            f.put("fileType", "invoice");
            f.put("fileName", fileName);
            String ext = FileUtil.extName(fileName);
            if (ext != null) {
                f.put("format", ext.toLowerCase()); // 白名单 fail-closed：配置了 upload-format-whitelist 时必须命中
            }
            f.put("size", bytes.length);
            f.put("digest", DigestUtil.sha256Hex(bytes));   // hex 小写，与服务端摘要算法一致
            f.put("digestAlg", "sha256");
            f.put("fetchMode", "push");
            fileDetails.add(f);
        }
        Map<String, Object> plain = new LinkedHashMap<>();
        plain.put("businessData", Map.of("orderNo", "SO-2026-0001", "amount", 88.5));
        plain.put("fileDetails", fileDetails);

        out.append('\n');
        out.append("═".repeat(76)).append('\n');
        out.append("【3】POST ").append(BASE).append("/secure/upload   （Body → form-data，共 ")
                .append(uploadFiles.size() + 1).append(" 行）\n\n");
        out.append("  第 1 行：key = meta，类型 Text，Value 整段粘贴；\n");
        out.append("          点行末 ⋮ 菜单 → Content-Type 填 application/json\n");
        out.append("  ").append("─".repeat(72)).append('\n');
        out.append(envelope("T030422", JSON.toJSONString(plain))).append('\n');
        out.append("  ").append("─".repeat(72)).append('\n');
        for (int i = 0; i < uploadFiles.size(); i++) {
            out.append("  第 ").append(i + 2).append(" 行：key = files，类型 File，选择 ")
                    .append(uploadFiles.get(i).toAbsolutePath()).append('\n');
            out.append("          （磁盘文件名即 fileId；meta 里声明的 digest 就是它的 sha256）\n");
        }
        out.append('\n');
        out.append("  注意：form-data 模式下不要手工加 Content-Type 请求头，Postman 会用 boundary 覆写；\n");
        out.append("  上面 4 个 x-api-* 头照常加。\n");
        return out.toString();
    }

    /** 组装加密信封：data 明文 SM4 加密（随机 16 位 IV 前缀）+ SM2 加签。 */
    static String envelope(String transType, String plain) {
        String data = SecurityUtil.sm4cbcEncrypt(plain, SECRET_KEY_SM4);
        String signature = SecurityUtil.sm2Base64Sign(plain, SM2_PRIVATE_KEY);
        return "{\"transNo\":\"" + UUID.randomUUID() + "\",\"transType\":\"" + transType
                + "\",\"data\":\"" + data + "\",\"signature\":\"" + signature + "\"}";
    }
}
