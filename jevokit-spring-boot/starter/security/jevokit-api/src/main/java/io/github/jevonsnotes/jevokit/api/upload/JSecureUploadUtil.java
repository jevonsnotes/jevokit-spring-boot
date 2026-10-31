package io.github.jevonsnotes.jevokit.api.upload;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.crypto.digest.SM3;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 上传协议静态工具：摘要算法适配（sha256/sm3）与 pull 域名白名单匹配、fileId 字符集白名单校验，
 * 服务端校验器（{@link JSecureUploadVerifier}）、拉取器（{@link JSecureFileFetcher}）
 * 与客户端构建器共用，保证两端摘要与 fileId 口径一致。
 *
 * <p>判定与返回语义：
 * <ul>
 *   <li>{@link #normalizeDigestAlg(String)}：空白归一为 sha256；非 sha256/sm3 抛协议异常（算法集白名单）；</li>
 *   <li>{@link #digestHex(InputStream, String)}：按算法流式计算 hex 小写摘要（流内已消费，调用方无须再读）；</li>
 *   <li>{@link #isUrlAllowed(java.util.List, String)}：URL 须为 http/https 且域名命中白名单
 *       （精确或 *.domain 通配）才放行；白名单未配置/URL 非法/未命中一律 false（fail-closed）；</li>
 *   <li>{@link #isFileIdAllowed(String)}：fileId 仅允许 {@value #FILE_ID_MAX_LENGTH} 个
 *       {@code [A-Za-z0-9._-]} 字符（multipart filename 的对齐键，防引号走私/CRLF 头注入/超长值），
 *       空白/非法字符/超长一律 false。</li>
 * </ul>
 */
public final class JSecureUploadUtil {

    /** 摘要算法：SHA-256。 */
    public static final String DIGEST_ALG_SHA256 = "sha256";
    /** 摘要算法：国密 SM3。 */
    public static final String DIGEST_ALG_SM3 = "sm3";
    /** fileId 合法字符集：字母、数字、点、下划线、连字符（multipart filename 走私/注入防线）。 */
    public static final String FILE_ID_ALLOWED_CHARS_DESC = "字母、数字与 . _ -";
    /** fileId 合法字符集正则（客户端入口与服务端元数据层共用同一口径）。 */
    private static final Pattern FILE_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");
    /** fileId 最大长度：128 字符（超长值拒绝，防 multipart part 头异常与日志噪音）。 */
    public static final int FILE_ID_MAX_LENGTH = 128;

    private JSecureUploadUtil() {
    }

    /**
     * fileId 字符集白名单校验（客户端入口与服务端元数据层共用）：
     * fileId 即 multipart 文件 part 的 filename（对齐键），含引号会走私出第二个 filename 属性、
     * 含 CRLF 会伪造 part 头行，非 Tomcat 解析器（代理/WAF）存在解析差异风险，故按白名单收紧。
     *
     * @param fileId 待校验的文件标识
     * @return true 表示仅含 {@code [A-Za-z0-9._-]} 且长度 1-{@value #FILE_ID_MAX_LENGTH}；
     *         空白/含中文或符号/超长均 false
     */
    public static boolean isFileIdAllowed(String fileId) {
        return StrUtil.isNotBlank(fileId)
                && fileId.length() <= FILE_ID_MAX_LENGTH
                && FILE_ID_PATTERN.matcher(fileId).matches();
    }

    /**
     * 归一化摘要算法名：空白视为默认 sha256；仅接受 sha256/sm3。
     *
     * @param digestAlg 报文内声明的算法名
     * @return 归一化后的小写算法名
     * @throws JSecureApiException 声明了不支持的算法时抛出
     */
    public static String normalizeDigestAlg(String digestAlg) {
        String normalized = StrUtil.trimToEmpty(digestAlg).toLowerCase();
        if (StrUtil.isBlank(normalized)) {
            return DIGEST_ALG_SHA256;
        }
        Assert.isTrue(DIGEST_ALG_SHA256.equals(normalized) || DIGEST_ALG_SM3.equals(normalized),
                () -> new JSecureApiException("不支持的摘要算法：" + digestAlg + "（仅支持 sha256/sm3）"));
        return normalized;
    }

    /**
     * 按算法流式计算摘要（hex 小写）。
     *
     * @param input     数据流（方法内完整消费）
     * @param digestAlg 摘要算法（sha256/sm3，空白归一为 sha256）
     * @return hex 小写摘要值
     * @throws JSecureApiException 算法不支持或计算失败时抛出
     */
    public static String digestHex(InputStream input, String digestAlg) {
        String alg = normalizeDigestAlg(digestAlg);
        try {
            return DIGEST_ALG_SM3.equals(alg) ? new SM3().digestHex(input) : DigestUtil.sha256Hex(input);
        } catch (Exception e) {
            throw new JSecureApiException("摘要计算失败：" + e.getMessage(), e);
        }
    }

    /**
     * 按算法计算字节摘要（hex 小写）——客户端构建器与单测用。
     *
     * @param content   数据内容
     * @param digestAlg 摘要算法（sha256/sm3，空白归一为 sha256）
     * @return hex 小写摘要值
     */
    public static String digestHex(byte[] content, String digestAlg) {
        String alg = normalizeDigestAlg(digestAlg);
        return DIGEST_ALG_SM3.equals(alg) ? new SM3().digestHex(content) : DigestUtil.sha256Hex(content);
    }

    /**
     * pull 拉取地址白名单匹配（SSRF 硬防线，服务端校验与拉取前复核共用同一口径）。
     *
     * @param whitelist 域名白名单（精确域名或 *.domain 通配）
     * @param url       待校验地址
     * @return true 表示 http/https 且域名命中白名单；白名单未配置、URL 非法或未命中均 false（fail-closed）
     */
    public static boolean isUrlAllowed(List<String> whitelist, String url) {
        if (StrUtil.isBlank(url) || whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        String host;
        try {
            URI uri = URI.create(url.trim());
            String scheme = StrUtil.nullToEmpty(uri.getScheme()).toLowerCase();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return false;
            }
            host = uri.getHost();
        } catch (Exception e) {
            return false;
        }
        if (StrUtil.isBlank(host)) {
            return false;
        }
        for (String pattern : whitelist) {
            String trimmed = StrUtil.trimToEmpty(pattern);
            if (StrUtil.isBlank(trimmed)) {
                continue;
            }
            if (trimmed.startsWith("*.")) {
                String domain = trimmed.substring(2);
                if (host.equalsIgnoreCase(domain) || (("." + host).endsWith("." + domain))) {
                    return true;
                }
            } else if (host.equalsIgnoreCase(trimmed)) {
                return true;
            }
        }
        return false;
    }
}
