package io.github.jevonsnotes.jevokit.api.util;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.MD5;
import cn.hutool.crypto.digest.SM3;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiProperties;
import io.github.jevonsnotes.jevokit.api.consts.ApiError;
import io.github.jevonsnotes.jevokit.api.consts.ApiHSignAlg;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;
import io.github.jevonsnotes.jevokit.core.util.SecurityUtil;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JSecure API 服务端/客户端共用的静态工具：终端配置查找、请求头拼名与请求头签名计算，
 * 以及客户端侧的协议要素组装（{@link #buildProtocolHeaders}/{@link #sealRequest}）与
 * 响应统一处理（{@link #handleResponse}）。
 * 使用方式：由 {@code JevokitApiConfiguration} 注册并注入 {@link JevokitApiProperties} 后静态调用；
 * 客户端发起安全请求的入口是 {@code JSecureClient.of(clientId)}（JSON 通道 post / 上传通道 upload）。
 * <p>判定与返回语义：
 * <ul>
 *   <li>{@link #getClient(String)}：按 id 精确匹配终端配置，找不到抛 {@link JSecureApiException}
 *       （fail-fast，不返回 null；与门面其余校验异常类型统一，消息含 clientId 便于定位）；</li>
 *   <li>{@link #getCurrentClient()}：遍历当前请求头，命中任一已登记终端的识别码头即返回该终端，
 *       无请求上下文或未命中返回 null（交由调用方决定放行或拒绝）；</li>
 *   <li>{@link #headerSignature(ApiHSignAlg, String, String, String)}：按 timestamp + secretKey + nonce 拼串后
 *       以指定算法（MD5/SHA256/SM3）摘要；{@link #checkHeaderSignature(String, ApiHSignAlg, String, String, String)}
 *       为恒等比对验签，失败记 ERROR 日志（含期望值与实际值，便于对端排查）；</li>
 *   <li>{@link #handleResponse(String, JevokitApiClientProperties, Class)}：解析响应信封并按终端开关完成
 *       解密（view）/反序列化（target）/验签，验签失败抛 {@link JSecureApiException}——不返回未验签的数据。</li>
 * </ul>
 * 约束：静态方法依赖容器完成构造注入（见 {@code JevokitApiConfiguration}），脱离 Spring 环境不可用。
 */
@Slf4j
public class JSecureApiUtil {
    private static JevokitApiProperties properties;

    /** 容器构造（注入配置启用静态方法，脱离 Spring 环境静态方法不可用）。 */
    public JSecureApiUtil(final JevokitApiProperties properties) {
        JSecureApiUtil.properties = properties;
    }

    /**
     * 按终端识别码精确匹配终端配置。
     *
     * @param clientId 终端识别码（jevokit.api.client[].id，区分大小写全等比较）
     * @return 对应终端配置
     * @throws JSecureApiException 无此终端时抛出（fail-fast，密钥配置缺失属部署错误；协议统一异常，消息含 clientId）
     */
    public static JevokitApiClientProperties getClient(String clientId) {
        return properties.getClient()
                .stream()
                .filter(jevokitApiClientProperties -> jevokitApiClientProperties.getId().equals(clientId))
                .findFirst()
                .orElseThrow(() -> new JSecureApiException("终端未登记（No client found with id）：" + clientId));
    }

    /**
     * 从当前请求头解析终端：任一已登记终端的识别码头命中即返回该终端。
     *
     * @return 命中的终端配置；无请求上下文或未命中返回 null（由调用方决定放行/拒绝）
     */
    public static JevokitApiClientProperties getCurrentClient() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attr != null) {
            HttpServletRequest request = attr.getRequest();
            Set<String> clientHeaders = properties.getClient()
                    .stream()
                    .map(properties -> ApiUtil.rebuildHeader(properties.getHeader().getClientIdName(), properties.getHeader().getPrefix()))
                    .collect(Collectors.toSet());
            // 获取终端
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                if (clientHeaders.contains(name)) {
                    String clientId = request.getHeader(name);
                    List<JevokitApiClientProperties> clients = properties.getClient().stream().filter(jevokitApiClientProperties -> jevokitApiClientProperties.getId().equals(clientId)).collect(Collectors.toList());
                    if (!clients.isEmpty()) {
                        return clients.get(0);
                    }
                }
            }
        }
        return null;
    }



    /**
     * 获取当前请求（Servlet 线程内有效）。
     *
     * @return 请求对象；非 Web 线程/无上下文返回 empty
     */
    public static Optional<HttpServletRequest> getCurrentRequest(){
        if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes){
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attr != null) {
                return Optional.of(attr.getRequest());
            }
        }
        return Optional.empty();
    }

    /**
     * 拼接协议头名：prefix 已以 - 结尾直接拼接，否则补 -（两种前缀写法产出一致头名）。
     *
     * @param key    头名主体（如 ClientId）
     * @param prefix 终端配置的头前缀（如 X-JS 或 X-JS-）
     * @return 实际头名（如 X-JS-ClientId）
     */
    public static String rebuildHeader(String key, String prefix) {
        return prefix.endsWith("-") ? prefix + key : prefix + "-" + key;
    }

    /**
     * 请求头验签：按同配方重算摘要与传入值恒等比对。
     *
     * @param signature 对端传入的签名值
     * @param alg       签名算法（MD5/SHA256/SM3）
     * @param timestamp 秒级时间戳（参与摘要原文）
     * @param nonce     随机码（参与摘要原文）
     * @param secretKey 终端密钥
     * @return true 验签通过；false 不匹配（记 ERROR 日志含期望/实际值，供对端排查）
     */
    public static boolean checkHeaderSignature(String signature, ApiHSignAlg alg, String timestamp, String nonce, String secretKey) {
        String headerSignActual = headerSignature(alg, timestamp, nonce, secretKey);
        boolean result = headerSignActual.equals(signature);
        if(!result){
            log.error("请求头验签失败 - expected {} actual {}", signature, headerSignActual);
        }
        return result;
    }

    /**
     * 计算请求头签名：digest(timestamp + secretKey + nonce)。
     *
     * @param alg       签名算法（MD5/SHA256/SM3）
     * @param timestamp 秒级时间戳字符串
     * @param nonce     随机码
     * @param secretKey 终端密钥
     * @return 十六进制摘要；alg 为 null 或不识别时返回空串（调用方以恒等比对判失败）
     */
    public static String headerSignature(ApiHSignAlg alg, String timestamp, String nonce, String secretKey) {
        String headerSignActual = StrUtil.EMPTY;
        String plaintext = timestamp + secretKey + nonce;
        if (ApiHSignAlg.MD5.equals(alg)) {
            headerSignActual = MD5.create().digestHex(plaintext);
        } else if (ApiHSignAlg.SHA256.equals(alg)) {
            headerSignActual = SecureUtil.sha256().digestHex(plaintext);
        } else if (ApiHSignAlg.SM3.equals(alg)) {
            headerSignActual = SM3.create().digestHex(plaintext);
        }
        return headerSignActual;
    }


    /**
     * 组装客户端外发的协议头四件套（client-id/timestamp/nonce/signature）：
     * timestamp 取秒级时间戳、nonce 取随机码，头签名按终端配置算法现算——
     * JSON 通道与上传通道共用同一配方（服务端按同一口径验签）。
     *
     * @param client 终端配置（与服务端成对登记）
     * @return 协议头（4 项，头名按终端 header 配置与前缀拼出）
     */
    public static Map<String, String> buildProtocolHeaders(JevokitApiClientProperties client) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = UUID.randomUUID().toString();
        String signature = headerSignature(client.getHeader().getSignAlg(), timestamp, nonce, client.getSecretKey());
        Map<String, String> headers = new HashMap<>(8);
        headers.put(rebuildHeader(client.getHeader().getClientIdName(), client.getHeader().getPrefix()), client.getId());
        headers.put(rebuildHeader(client.getHeader().getTimestampName(), client.getHeader().getPrefix()), timestamp);
        headers.put(rebuildHeader(client.getHeader().getNonceName(), client.getHeader().getPrefix()), nonce);
        headers.put(rebuildHeader(client.getHeader().getSignatureName(), client.getHeader().getPrefix()), signature);
        return headers;
    }

    /**
     * 客户端数据体签名加密入信封：数据明文 SM2 私钥签名写入 signature，
     * 按终端开关 SM4 加密写入 data（关闭时原文直传）；transNo/transType 由调用方预置。
     *
     * @param request 协议信封（签名与密文写入本对象）
     * @param dataStr 数据明文（业务对象/上传业务体的 JSON 串）
     * @param client  终端配置（私钥签名、SM4 密钥取自终端）
     */
    public static void sealRequest(JSecureRequest<?> request, String dataStr, JevokitApiClientProperties client) {
        request.setSignature(SecurityUtil.sm2Base64Sign(dataStr, client.getPrivateKey()));
        request.setData(client.isEncrypt() ? SecurityUtil.sm4cbcEncrypt(dataStr, client.getSecretKeySm4()) : dataStr);
    }

    /**
     * 客户端响应统一处理（JSON/上传两通道同源调用，勿在通道内复制）：
     * 解析响应信封 → 按终端开关解密 data 为 view → 反序列化为 target（按返回类型）→
     * 按终端开关 SM2 公钥验签 view，失败抛 {@link JSecureApiException}——不返回未验签的数据。
     * <p>异常语义：响应体非 JSON（网关 502 错误页/截断报文/空串）或解密反序列化失败时，
     * 同样抛 {@link JSecureApiException}（消息含响应体长度与前 64 字符摘要，不回显整个响应体，
     * 防日志噪音与信息暴露）；内部已抛出的 {@link JSecureApiException}（如验签失败）原样透出。
     *
     * @param res           响应体原文（信封密文 JSON）
     * @param client        终端配置（SM4 密钥解密、公钥验签）
     * @param responseClass 返回数据类型（target 反序列化目标）
     * @param <R>           返回数据类型
     * @return 已解密验签的协议响应（view/target 就绪；data 为空的响应仅回填信封字段）
     * @throws JSecureApiException 响应解析/解密/反序列化失败或验签失败时抛出
     */
    public static <R> JSecureResponse<R> handleResponse(String res, JevokitApiClientProperties client,
                                                        Class<R> responseClass) {
        try {
            return doHandleResponse(res, client, responseClass);
        } catch (JSecureApiException e) {
            // 协议校验失败（验签失败等）原样透出，避免二次包装掩盖原因
            throw e;
        } catch (Exception e) {
            // 非 JSON 响应体（fastjson 异常消息会回显整个响应体）或解密/反序列化失败：统一协议异常，
            // 消息只带响应体长度与前 64 字符，不回显全量响应体
            log.warn("JSecure 响应处理失败（{}）：{}", responseBrief(res), e.getClass().getSimpleName());
            throw new JSecureApiException(
                    "响应解析失败：" + e.getClass().getSimpleName() + "（" + responseBrief(res) + "）", e);
        }
    }

    /**
     * {@link #handleResponse} 的实际处理逻辑（异常统一由外层包装）。
     */
    private static <R> JSecureResponse<R> doHandleResponse(String res, JevokitApiClientProperties client,
                                                           Class<R> responseClass) {
        JSecureResponse<R> response = JSONObject.parseObject(res, new TypeReference<JSecureResponse<R>>() {
        });
        Assert.notNull(response, () -> new JSecureApiException("响应体为空或非 JSON 协议信封"));
        String dataStr = response.getData();
        if (StrUtil.isNotBlank(dataStr)) {
            // 数据体解密
            if (client.isDecrypt()) {
                response.setView(SecurityUtil.sm4cbcDecrypt(dataStr, client.getSecretKeySm4()));
            } else {
                response.setView(dataStr);
            }
            response.setTarget(JSONObject.parseObject(response.getView(), responseClass));
            // 数据体验签
            if (client.isVerify()) {
                boolean verify = SecurityUtil.sm2Base64Verify(response.getView(), response.getSignature(), client.getPublicKey());
                if (!verify) {
                    log.error("返回结果验签失败: {}", res);
                    throw new JSecureApiException(ApiError.FAIL.code(), "返回结果验签失败");
                }
            }
        }
        return response;
    }

    /**
     * 响应体概要（异常消息与日志用）：仅长度 + 前 64 字符，不回显全量响应体
     * （网关错误页/错误报文可能携带敏感信息或超长内容）。
     */
    private static String responseBrief(String res) {
        if (res == null) {
            return "响应体为 null";
        }
        String head = res.length() <= RESPONSE_BRIEF_MAX_LENGTH ? res : res.substring(0, RESPONSE_BRIEF_MAX_LENGTH);
        return "length=" + res.length() + ", head=" + head;
    }

    /** 响应体概要回显的最大前缀长度（字符）。 */
    private static final int RESPONSE_BRIEF_MAX_LENGTH = 64;

    /**
     * 默认载体（hutool）发送后的 HTTP 状态断言：非 2xx 即抛 {@link JSecureApiException}
     * （hutool 对非 2xx 不抛异常、body 原样返回，网关 404/500 错误页会被误当协议响应体送入解析）。
     * 消息含状态码与 URL、不回显响应体，使传输层失败与协议层失败（响应解析/验签）可区分。
     *
     * @param status 实际 HTTP 状态码
     * @param url    请求地址（消息定位用）
     * @throws JSecureApiException 状态码非 2xx 时抛出
     */
    public static void assertHttpSuccess(int status, String url) {
        if (status < 200 || status >= 300) {
            throw new JSecureApiException("JSecure 请求传输失败：HTTP 状态码 " + status + "，URL：" + url);
        }
    }

}

