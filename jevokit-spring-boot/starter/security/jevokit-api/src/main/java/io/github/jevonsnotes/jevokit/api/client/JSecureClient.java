package io.github.jevonsnotes.jevokit.api.client;

import cn.hutool.core.lang.Assert;
import io.github.jevonsnotes.jevokit.api.consts.ApiError;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;

/**
 * JSecure 协议客户端统一门面："作为调用方"发起一次安全请求的唯一入口，
 * 两通道分道——{@link #post(String, Object, Class)} 走 JSON 通道（{@code @RequestBody} 信封），
 * {@link #upload(String, Object, Class)} 走上传通道（multipart：meta part 加密信封 + 文件 part）。
 * 使用方式：
 * <pre>{@code
 * // JSON 通道
 * JSecureResponse<OrderVo> resp = JSecureClient.of(clientId)
 *         .post("/api/order/detail", bizDto, OrderVo.class)
 *         .transNo("t-001").transType("QUERY")
 *         .execute();                          // 空参走默认载体（hutool）
 *
 * // 上传通道（push/pull/refer 混合）
 * JSecureResponse<UploadVo> upload = JSecureClient.of(clientId)
 *         .upload("/secure/upload", orderBiz, UploadVo.class)
 *         .transNo("t-002")
 *         .add(invoiceFile, "inv-1", "invoice")          // push：自动算 size/format/digest
 *         .pull("oss-1", fileUrl, size, digest)          // pull：URL 拉取
 *         .refer("idc-10086")                            // refer：引用已有
 *         .execute();
 * }</pre>
 * <p>判定与返回语义：终端配置按 clientId 从 jevokit.api.client 列表匹配（prepare 时缺失即抛错 fail-fast）；
 * execute 返回的 {@code JSecureResponse<R>} 已完成解密（view）与反序列化（target），
 * 终端开启 verify 时对响应验签，失败抛 {@link JSecureApiException}——不返回未验签的数据。
 * <p>传输载体：空参 {@code execute()} 走默认载体（hutool HttpRequest，JSON 报文 / multipart 表单）；
 * 需要自选 HTTP 客户端（OkHttp/RestTemplate/MockMvc 等）时经 {@code execute(IRequest)} /
 * {@code execute(IUploadRequest)} 注入回调，门面只做协议组装与结果解析。
 * <p>关键约束：请求头签名与数据签名的密钥均来自终端配置（secretKey/私钥），调用方与服务端须成对登记。
 */
public class JSecureClient {

    /** 终端识别码（jevokit.api.client[].id）。 */
    private final String clientId;

    private JSecureClient(String clientId) {
        Assert.notBlank(clientId, () -> new JSecureApiException(ApiError.FAIL.code(), "终端识别码不能为空"));
        this.clientId = clientId;
    }

    /**
     * 创建一个客户端门面。
     *
     * @param clientId 终端识别码（须在 jevokit.api.client 列表内登记，prepare 时 fail-fast 校验）
     * @return 门面（后续经 post/upload 选定通道）
     */
    public static JSecureClient of(String clientId) {
        return new JSecureClient(clientId);
    }

    /**
     * 选定 JSON 通道：目标地址与数据形态在此定型，链式补交易要素后 execute。
     *
     * @param url          请求地址（如 https://server-host/api/order/detail）
     * @param data         业务数据对象（任意 DTO 或 Map，序列化后走 SM2 签名 + SM4 加密信封）
     * @param responseType 返回数据类型（响应解密后反序列化目标）
     * @param <T>          请求数据类型
     * @param <R>          返回数据类型
     * @return JSON 通道构建器
     */
    public <T, R> JsonChannel<T, R> post(String url, T data, Class<R> responseType) {
        return new JsonChannel<>(clientId, url, data, responseType);
    }

    /**
     * 选定上传通道：目标地址与业务数据在此定型，链式追加文件条目（add/pull/refer）后 execute。
     *
     * @param url          请求地址（服务端为 meta + files 双参数端点）
     * @param businessData 业务数据（任意 DTO 或 Map，与文件清单一并走加密信封）
     * @param responseType 返回数据类型（响应解密后反序列化目标）
     * @param <B>          业务数据类型
     * @param <R>          返回数据类型
     * @return 上传通道构建器
     */
    public <B, R> UploadChannel<B, R> upload(String url, B businessData, Class<R> responseType) {
        return new UploadChannel<>(clientId, url, businessData, responseType);
    }
}
