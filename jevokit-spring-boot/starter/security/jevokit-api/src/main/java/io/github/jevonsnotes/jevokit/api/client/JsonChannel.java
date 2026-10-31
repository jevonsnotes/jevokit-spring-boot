package io.github.jevonsnotes.jevokit.api.client;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import io.github.jevonsnotes.jevokit.api.callback.IRequest;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * JSecure 协议 JSON 通道构建器（经 {@link JSecureClient#post(String, Object, Class)} 获得）：
 * 封装"作为调用方"发起一次 JSON 安全请求的组装与执行——请求头签名、数据 SM2 签名、
 * SM4 加密与响应解密验签，报文形态为 {@code @RequestBody} 的 {@link JSecureRequest} 信封。
 * <p>使用方式：链式补交易要素后 execute；{@code execute()} 空参走默认载体（hutool HttpRequest：
 * 协议头 headerMap + JSON body，非 2xx 状态码即抛协议异常），{@code execute(IRequest)} 注入自定义传输载体回调；
 * execute 前未调用 {@link #prepare()} 会自动补做。
 * <p>关键约束：execute 返回的 {@code JSecureResponse<R>} 已完成解密（view）与反序列化（target），
 * 终端开启 verify 时对响应验签，失败抛 {@link JSecureApiException}——不返回未验签的数据。
 */
public class JsonChannel<T, R> {

    /** 终端识别码。 */
    private final String clientId;

    /** 请求地址。 */
    @Getter
    private final String url;

    /** 业务数据对象。 */
    @Getter
    private final T data;

    /** 返回数据类型。 */
    @Getter
    private final Class<R> responseClass;

    /** 协议请求信封（transNo/transType 链式写入，prepare 后含签名与密文）。 */
    @Getter
    private final JSecureRequest<T> request = new JSecureRequest<>();

    /** 协议头（client-id/timestamp/nonce/signature，prepare 时生成）。 */
    @Getter
    private final Map<String, String> headers = new HashMap<>();

    /** 终端配置（prepare 时按 clientId 匹配）。 */
    private JevokitApiClientProperties client;

    /** prepare 标记（已组装则幂等跳过，execute 自动补做）。 */
    @Getter
    private boolean prepared;

    JsonChannel(String clientId, String url, T data, Class<R> responseClass) {
        this.clientId = clientId;
        this.url = url;
        this.data = data;
        this.responseClass = responseClass;
    }

    /**
     * 设置交易号（不设则 prepare 时自动生成 UUID——服务端 transNo 为 @NotBlank 必填，
     * 由客户端兜底生成保证报文恒可过校验）。
     *
     * @param transNo 交易号
     * @return 本构建器（链式）
     */
    public JsonChannel<T, R> transNo(String transNo) {
        request.setTransNo(transNo);
        return this;
    }

    /**
     * 设置交易类型（服务端 @JSecureApi 声明了 transType 限定时必填，否则校验不过）。
     *
     * @param transType 交易类型
     * @return 本构建器（链式）
     */
    public JsonChannel<T, R> transType(String transType) {
        request.setTransType(transType);
        return this;
    }

    /**
     * 组装协议要素：按 clientId 取终端配置，transNo 未设时自动生成 UUID（服务端 @NotBlank 必填），
     * 生成时间戳/nonce/请求头签名，数据 SM2 签名并按终端开关 SM4 加密
     * （幂等：已组装则原样返回，便于调用方定点篡改后重发）。
     *
     * @return 本构建器（prepared 标记置真）
     * @throws JSecureApiException clientId 为空或终端未登记时抛出
     */
    public JsonChannel<T, R> prepare() {
        if (prepared) {
            return this;
        }
        client = JSecureApiUtil.getClient(clientId);
        // transNo 不设则自动生成：服务端 @NotBlank 无条件必填，客户端兜底保证报文恒可过校验
        if (StrUtil.isBlank(request.getTransNo())) {
            request.setTransNo(UUID.randomUUID().toString());
        }
        headers.putAll(JSecureApiUtil.buildProtocolHeaders(client));
        JSecureApiUtil.sealRequest(request, JSON.toJSONString(data), client);
        prepared = true;
        return this;
    }

    /**
     * 默认载体发送：hutool HttpRequest 携协议头 POST JSON 报文，响应走统一解密验签。
     * 非 2xx 状态码（网关 404/500 错误页）在此即抛 {@link JSecureApiException}（传输层失败），
     * 不把错误页当响应体送入协议解析。
     *
     * @return 已解密验签的协议响应
     * @throws JSecureApiException 发送失败、HTTP 非 2xx、响应解析失败或验签失败时抛出
     */
    public JSecureResponse<R> execute() {
        return execute((url, body, headers) -> {
            try {
                HttpResponse response = HttpRequest.post(url)
                        .headerMap(headers, true)
                        .body(body, "application/json")
                        .execute();
                // hutool 对非 2xx 不抛异常（body 原样返回，404 还会捏造错误文本），此处显式判定传输层失败
                JSecureApiUtil.assertHttpSuccess(response.getStatus(), url);
                return response.body();
            } catch (JSecureApiException e) {
                throw e;
            } catch (Exception e) {
                throw new JSecureApiException("JSecure 请求发送失败：" + url, e);
            }
        });
    }

    /**
     * 发起请求并处理响应：未 prepare 自动补做；经 {@link IRequest} 发送报文，
     * 响应解密（view）、反序列化（target）并按终端开关验签（与上传通道同源处理）。
     *
     * @param callback 传输载体回调（任意 HTTP 客户端实现，headers 须原样透传）
     * @return 已解密验签的协议响应
     * @throws JSecureApiException 响应验签失败时抛出（不返回未验签数据）
     */
    public JSecureResponse<R> execute(IRequest callback) {
        prepare();
        String res = callback.call(url, JSON.toJSONString(request), headers);
        return JSecureApiUtil.handleResponse(res, client, responseClass);
    }
}
