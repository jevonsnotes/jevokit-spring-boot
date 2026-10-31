package io.github.jevonsnotes.jevokit.api.upload;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.context.JSecureContext;
import io.github.jevonsnotes.jevokit.api.dto.FetchMode;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDto;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * pull 模式文件拉取器：对已通过元数据层校验（含白名单）的 pull 条目做<b>业务显式</b>的受控下载——
 * 白名单复核 → 带 X-Fetch-Token（可选）下载 → size 预检 → 流式摘要复核 → 返回字节（落盘/转存由调用方决定）。
 *
 * <p><b>下载时机是业务决策</b>（同步拉取/异步任务/失败重试各场景不同），框架不做时机决策、
 * 也不自动拉取——业务在需要文件内容的时机调用 {@link #fetch(JSecureUploadFileDto)}。
 *
 * <p>安全边界（SSRF 纵深）：域名白名单在入口校验与本处复核各执行一次；仅允许 http/https；
 * 禁止跟随重定向（3xx 一律拒绝，防止 302 跳出白名单域）；下载字节数以声明 size 封顶，
 * 超限即拒（防解压炸弹式拖库）。
 */
@Slf4j
public class JSecureFileFetcher {

    /** fetchToken 透传头名（源站按需校验）。 */
    public static final String FETCH_TOKEN_HEADER = "X-Fetch-Token";

    /** 连接超时（毫秒）。 */
    private static final long CONNECT_TIMEOUT_MILLIS = 10_000L;
    /** 单请求超时（毫秒）。 */
    private static final long REQUEST_TIMEOUT_MILLIS = 60_000L;
    /** 流式读取缓冲区大小。 */
    private static final int READ_BUFFER_SIZE = 8192;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MILLIS))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * 拉取 pull 文件（终端取当前请求上下文）。
     *
     * @param file pull 文件元数据条目
     * @return 文件字节（已通过 size/摘要复核）
     * @throws JSecureApiException 非请求线程、非 pull 条目、白名单外地址或下载/复核失败时抛出
     */
    public byte[] fetch(JSecureUploadFileDto file) {
        return fetch(file, JSecureContext.getCurrentClientThrowIfInvalid());
    }

    /**
     * 拉取 pull 文件（显式指定终端，适合异步线程等无请求上下文场景）。
     *
     * @param file   pull 文件元数据条目
     * @param client 终端配置（白名单/上限来源）
     * @return 文件字节（已通过 size/摘要复核）
     * @throws JSecureApiException 非 pull 条目、白名单外地址或下载/复核失败时抛出
     */
    public byte[] fetch(JSecureUploadFileDto file, JevokitApiClientProperties client) {
        Assert.notNull(file, () -> new JSecureApiException("拉取文件条目不能为空"));
        Assert.notNull(client, () -> new JSecureApiException("拉取文件需要终端配置"));
        Assert.isTrue(FetchMode.pull.equals(file.getFetchMode()), () -> new JSecureApiException("仅支持拉取 pull 模式文件"));
        Assert.notBlank(file.getUrl(), () -> new JSecureApiException("pull 文件 url 不能为空"));
        // 白名单复核（入口校验之后的第二道，防配置变更窗口内漏放）
        Assert.isTrue(JSecureUploadUtil.isUrlAllowed(client.getFetchUrlWhitelist(), file.getUrl()),
                () -> new JSecureApiException("pull 文件地址不在白名单：" + JSecureUploadLogMasker.maskUrl(file.getUrl())));
        Long declaredSize = file.getSize();
        Assert.isTrue(declaredSize == null || declaredSize >= 0, () -> new JSecureApiException("pull 文件 size 非法"));
        Assert.isTrue(declaredSize == null || declaredSize <= client.getUploadMaxSize(),
                () -> new JSecureApiException("pull 文件超出大小上限：" + declaredSize));

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(file.getUrl().trim()))
                .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MILLIS))
                .GET();
        if (StrUtil.isNotBlank(file.getFetchToken())) {
            requestBuilder.header(FETCH_TOKEN_HEADER, file.getFetchToken());
        }

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new JSecureApiException("文件下载失败（IO）：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JSecureApiException("文件下载被中断", e);
        }
        int status = response.statusCode();
        Assert.isTrue(status >= 200 && status < 300, () -> new JSecureApiException("文件下载失败，源站响应码：" + status));

        byte[] content;
        try (InputStream body = response.body()) {
            content = readCapped(body, declaredSize);
        } catch (IOException e) {
            throw new JSecureApiException("文件读取失败：" + e.getMessage(), e);
        }
        if (declaredSize != null) {
            Assert.isTrue(content.length == declaredSize, () -> new JSecureApiException(
                    "文件大小与声明不符（声明 " + declaredSize + "，实际 " + content.length + "）"));
        }
        String digestAlg = JSecureUploadUtil.normalizeDigestAlg(file.getDigestAlg());
        if (StrUtil.isNotBlank(file.getDigest())) {
            String actualDigest = JSecureUploadUtil.digestHex(content, digestAlg);
            Assert.isTrue(actualDigest.equalsIgnoreCase(StrUtil.trimToEmpty(file.getDigest())),
                    () -> new JSecureApiException("文件摘要校验失败（url=" + JSecureUploadLogMasker.maskUrl(file.getUrl()) + "）"));
        }
        log.info("JSecureFileFetcher 拉取完成：fileId={}, size={}B", file.getFileId(), content.length);
        return content;
    }

    /**
     * 流式读取并按声明 size 封顶（声明缺失时按终端上限封顶；超限即拒，防止超大响应拖垮内存）。
     */
    private byte[] readCapped(InputStream body, Long declaredSize) throws IOException {
        long cap = declaredSize != null ? declaredSize : Long.MAX_VALUE;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[READ_BUFFER_SIZE];
        long total = 0;
        int read;
        while ((read = body.read(chunk)) != -1) {
            total += read;
            if (total > cap) {
                throw new JSecureApiException("文件实际大小超出声明 size，已中止下载");
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
