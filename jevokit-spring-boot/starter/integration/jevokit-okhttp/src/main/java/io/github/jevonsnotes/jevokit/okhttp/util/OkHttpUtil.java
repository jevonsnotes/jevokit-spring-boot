package io.github.jevonsnotes.jevokit.okhttp.util;

import cn.hutool.core.clone.CloneSupport;
import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import io.github.jevonsnotes.jevokit.core.exception.IORuntimeException;
import io.github.jevonsnotes.jevokit.core.exception.OkHttpRuntimeException;
import io.github.jevonsnotes.jevokit.core.exception.OkHttpStatusRuntimeException;
import io.github.jevonsnotes.jevokit.core.util.CollectionUtil;
import io.github.jevonsnotes.jevokit.core.util.HttpUtil;
import io.github.jevonsnotes.jevokit.okhttp.config.OkhttpProperties;
import io.github.jevonsnotes.jevokit.okhttp.config.JevokitOkhttpConfiguration;
import io.github.jevonsnotes.jevokit.okhttp.dto.FormDataPart;
import io.github.jevonsnotes.jevokit.okhttp.interceptor.CustomOkhttpConfiguration;
import io.github.jevonsnotes.jevokit.okhttp.interceptor.CustomOkhttpProxyConfiguration;
import io.github.jevonsnotes.jevokit.okhttp.lang.HeaderBuilder;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

 /**
 * OkHttp 调用门面：GET / POST / JSON / XML / 表单 / multipart 分片 / 下载等常用请求形态的一站式封装，
 * 由 jevokit-okhttp-spring-boot-starter 装配为单例 Bean 直接注入；个性化定制走 CustomOkhttpConfiguration
 * Bean 改写底层 Builder，或经 ofProxy(...) 克隆出携带自定义客户端的独立副本（CloneSupport 深拷贝）。
 *
 * <p>两类行为由 jevokit.ok.http.* 配置驱动，语义边界务必区分：
 * <ul>
 *   <li><b>proxy-mode</b>：always 一律走代理客户端；manual 默认直连，需代理时调用 {@link #ofProxy()}
 *       或 {@link #ofProxy(CustomOkhttpProxyConfiguration)} 显式切换；</li>
 *   <li><b>error-mode</b>：IO 异常与非 2xx 的统一处置，文本/字节/流三路径语义一致。throwout（默认）
 *       非 2xx 抛 OkHttpStatusRuntimeException、IO 异常抛 OkHttpRuntimeException/IORuntimeException；
 *       quiet / log_simple / log_detail 吞掉不向调用方抛错——文本路径返回空串或错误体，
 *       字节/流路径返回 null，调用方必须以返回值判成败。</li>
 * </ul>
 *
 * <p>关键约束：底层客户端 retryOnConnectionFailure=false（禁用自动重试，避免超时场景的重复请求）；
 * downloadAsStream 系列返回的流所有权移交调用方，必须自行关闭。
 */

@Slf4j
public class OkHttpUtil extends CloneSupport<OkHttpUtil> implements ApplicationListener<ContextRefreshedEvent> {
    private final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final MediaType XML = MediaType.parse("application/xml; charset=utf-8");
    private OkHttpClient okHttpClient;
    private OkHttpClient okHttpProxyClient;
    private final OkhttpProperties properties;
    //    private final ThreadLocal<Boolean> PROXY_MODE = new ThreadLocal<>();
    private OkHttpClient customOkhttpClient;
    /** 容器就绪后提升的门面静态引用（DEF-1 定式统一，遗留 #1）：ContextRefreshedEvent 发布、
     *  ContextClosedEvent 清理（防 spring-test 多上下文/热重启残留）。of() 家族优先消费静态引用，
     *  未发布（刷新窗口内）回落 SpringUtil.getBean——门面仅由业务线程调用，窗口期内的调用为主线程
     *  重入查找（安全），不存在日志链路式的外部线程等待环；静态化后刷新完成的常态路径零容器查找 */
    private static volatile OkHttpUtil staticInstance;


     /**
     * 构造门面实例（由 JevokitOkhttpConfiguration 装配调用，业务侧注入使用而非自建）
     * @param properties 行为配置（error-mode/代理模式）
     * @param okHttpClient 直连客户端
     * @param okHttpProxyClient 代理客户端（多代理自动切换）
     * @return 无（构造器）
     */
    public OkHttpUtil(OkhttpProperties properties, OkHttpClient okHttpClient, OkHttpClient okHttpProxyClient) {
        this.okHttpClient = okHttpClient;
        this.okHttpProxyClient = okHttpProxyClient;
        this.properties = properties;
    }

    /**
     * get 请求
     *
     * @param url 请求url地址
     * @return string
     */
    public String get(String url) {
        return get(url, null, null);
    }

     /**
     * GET 取字节（二进制下载）。语义同 {@link #getByte(String, Map, Map)} 便捷形态
     * @param url 请求地址
     * @return 返回响应体字节；throwout 模式异常同上抛出；quiet/log 吞错时异常返回 <b>null</b>
     *          （注意与文本方法的"空串"差异，判空须用引用比较）
     */
    public byte[] getByte(String url) {
        return getByte(url, null, null);
    }

    /**
     * get 请求
     *
     * @param url    请求url地址
     * @param params 请求参数 map
     * @return string
     */
    public String getWithParams(String url, Map<String, String> params) {
        return get(url, params, null);
    }

    /**
     * get 请求
     *
     * @param url     请求url地址
     * @param headers 请求头字段 {k1, v1 k2, v2, ...}
     * @return string
     */
    public String getWithHeaders(String url, Map<String, String> headers) {
        return get(url, null, headers);
    }

    /**
     * get 请求
     *
     * @param url     请求url地址
     * @param params  请求参数 map
     * @param headers 请求头字段 {k1, v1 k2, v2, ...}
     * @return string
     */
    public String get(String url, Map<String, String> params, Map<String, String> headers) {
        return executeBody(prepareGetRequest(url, params, headers));
    }

     /**
     * GET 取字节全参形态（二进制内容，响应不按文本解码）
     * @param url 请求地址
     * @param params 查询参数，拼入 URL，可空
     * @param headers 请求头，可空
     * @return 返回响应体字节；throwout 模式异常同上抛出；quiet/log 吞错时异常返回 <b>null</b>
     *          （注意与文本方法的"空串"差异，判空须用引用比较）
     */
    public byte[] getByte(String url, Map<String, String> params, Map<String, String> headers) {
        return executeByte(prepareGetRequest(url, params, headers));
    }

    /**
    * 组装 GET 请求（参数拼 URL + 请求头）。
    */
    private Request prepareGetRequest(String url, Map<String, String> params, Map<String, String> headers) {
        return buildHeaders(headers).url(buildUrlByQueryParams(url, params)).build();
    }


     /**
     * 空体 POST（无参数无请求头），UTF-8。语义同 {@link #post(String, Map, Map, Charset)} 便捷形态
     * @param url 请求地址
     * @return 返回语义由 error-mode 支配（见类注释）：throwout 抛 OkHttpStatusRuntimeException（非 2xx）
     *         或 OkHttpRuntimeException/IORuntimeException（IO 异常）；quiet/log 模式吞错——异常时返回空串、
     *         非 2xx 时返回错误体文本，<b>永不返回 null</b>，调用方须以返回值判成败
     */
    public String post(String url) {
        return post(url, null, StandardCharsets.UTF_8);
    }

     /**
     * 空体 POST，指定字符集。语义同 {@link #post(String, Map, Map, Charset)} 便捷形态
     * @param url 请求地址
     * @param charset 表单参数编码 + 响应解码字符集，null 走 UTF-8/自动探测
     * @return 返回语义由 error-mode 支配（见类注释）：throwout 抛 OkHttpStatusRuntimeException（非 2xx）
     *         或 OkHttpRuntimeException/IORuntimeException（IO 异常）；quiet/log 模式吞错——异常时返回空串、
     *         非 2xx 时返回错误体文本，<b>永不返回 null</b>，调用方须以返回值判成败
     */
    public String post(String url, Charset charset) {
        return post(url, null, charset);
    }

     /**
     * 表单 POST（参数以 application/x-www-form-urlencoded 提交），UTF-8。语义同全参形态便捷版本
     * @param url 请求地址
     * @param params 表单参数，null/空则发空体
     * @return 返回语义由 error-mode 支配（见类注释）：throwout 抛 OkHttpStatusRuntimeException（非 2xx）
     *         或 OkHttpRuntimeException/IORuntimeException（IO 异常）；quiet/log 模式吞错——异常时返回空串、
     *         非 2xx 时返回错误体文本，<b>永不返回 null</b>，调用方须以返回值判成败
     */
    public String post(String url, Map<String, String> params) {
        return post(url, params, StandardCharsets.UTF_8);
    }

     /**
     * 原始报文体 POST：contentType 缺省按 text/html + charset 补齐，已含 charset 则原样使用
     * @param url 请求地址
     * @param body 原始报文体
     * @param contentType Content-Type 值，空串补 text/html; charset=…
     * @param charset 报文体与响应解码字符集，null 走 UTF-8
     * @return 返回语义由 error-mode 支配（见类注释）：throwout 抛 OkHttpStatusRuntimeException（非 2xx）
     *         或 OkHttpRuntimeException/IORuntimeException（IO 异常）；quiet/log 模式吞错——异常时返回空串、
     *         非 2xx 时返回错误体文本，<b>永不返回 null</b>，调用方须以返回值判成败
     */
    public String postBody(String url, String body, String contentType, Charset charset) {
        return postBody(url, body, null, contentType, charset);
    }

     /**
     * 原始报文体 POST 全参形态（带请求头）
     * @param url 请求地址
     * @param body 原始报文体
     * @param headers 请求头，可空
     * @param contentType Content-Type 值，空串补 text/html; charset=…（charset 参数参与拼接）
     * @param charset 报文体与响应解码字符集，null 走 UTF-8
     * @return 返回语义由 error-mode 支配（见类注释）：throwout 抛 OkHttpStatusRuntimeException（非 2xx）
     *         或 OkHttpRuntimeException/IORuntimeException（IO 异常）；quiet/log 模式吞错——异常时返回空串、
     *         非 2xx 时返回错误体文本，<b>永不返回 null</b>，调用方须以返回值判成败
     */
    public String postBody(String url, String body, Map<String, String> headers, String contentType, Charset charset) {
        charset = ObjectUtil.defaultIfNull(charset, StandardCharsets.UTF_8);
        MediaType mediaType = StrUtil.isBlank(contentType) ? MediaType.parse("text/html; charset=" + charset.name()) : contentType.toUpperCase().contains("CHARSET") ? MediaType.parse(contentType) : MediaType.parse(String.format("%s; charset=%s", contentType, charset.name()));
        RequestBody requestBody = RequestBody.create(body, mediaType);
        Request request = buildHeaders(headers).url(url).post(requestBody).build();
        return execute(request, charset);
    }

    /**
     * post 请求
     *
     * @param url    请求url地址
     * @param params 请求参数 map
     * @return string
     */
    public String postForm(String url, Map<String, String> params) {
        return post(url, params, null, null);
    }

     /**
     * multipart 文件分片上传（无附加表单字段）。语义同 {@link #postFormDataPart(String, Map, Map, List, String)} 便捷形态
     * @param url 请求地址
     * @param parts 文件分片清单，file 与 mediaType 必填（缺省断言失败）
     * @return 返回语义由 error-mode 支配（见类注释）：throwout 抛 OkHttpStatusRuntimeException（非 2xx）
     *         或 OkHttpRuntimeException/IORuntimeException（IO 异常）；quiet/log 模式吞错——异常时返回空串、
     *         非 2xx 时返回错误体文本，<b>永不返回 null</b>，调用方须以返回值判成败
     */
    public String postFormDataPart(String url, List<FormDataPart> parts) {
        return postFormDataPart(url, null, null, parts, null);
    }

     /**
     * multipart 上传（表单字段 + 文件分片）。语义同全参形态便捷版本
     * @param url 请求地址
     * @param params 普通表单字段，可空
     * @param parts 文件分片清单
     * @return 返回语义由 error-mode 支配（见类注释）：throwout 抛 OkHttpStatusRuntimeException（非 2xx）
     *         或 OkHttpRuntimeException/IORuntimeException（IO 异常）；quiet/log 模式吞错——异常时返回空串、
     *         非 2xx 时返回错误体文本，<b>永不返回 null</b>，调用方须以返回值判成败
     */
    public String postFormDataPart(String url, Map<String, String> params, List<FormDataPart> parts) {
        return postFormDataPart(url, params, null, parts, null);
    }

     /**
     * multipart 上传（表单字段 + 单个文件分片）。语义同全参形态便捷版本
     * @param url 请求地址
     * @param params 普通表单字段，可空
     * @param part 单个文件分片
     * @return 返回语义由 error-mode 支配（见类注释）：throwout 抛 OkHttpStatusRuntimeException（非 2xx）
     *         或 OkHttpRuntimeException/IORuntimeException（IO 异常）；quiet/log 模式吞错——异常时返回空串、
     *         非 2xx 时返回错误体文本，<b>永不返回 null</b>，调用方须以返回值判成败
     */
    public String postFormDataPart(String url, Map<String, String> params, FormDataPart part) {
        return postFormDataPart(url, params, null, CollectionUtil.toList(part), null);
    }

     /**
     * multipart 上传（单分片 + 自定义边界）。语义同全参形态便捷版本
     * @param url 请求地址
     * @param params 普通表单字段，可空
     * @param part 单个文件分片
     * @param boundary multipart 边界，空则由 OkHttp 随机生成（对端有固定边界要求时指定）
     * @return 返回语义由 error-mode 支配（见类注释）：throwout 抛 OkHttpStatusRuntimeException（非 2xx）
     *         或 OkHttpRuntimeException/IORuntimeException（IO 异常）；quiet/log 模式吞错——异常时返回空串、
     *         非 2xx 时返回错误体文本，<b>永不返回 null</b>，调用方须以返回值判成败
     */
    public String postFormDataPart(String url, Map<String, String> params, FormDataPart part, String boundary) {
        return postFormDataPart(url, params, null, CollectionUtil.toList(part), boundary);
    }

     /**
     * multipart 上传（多分片 + 自定义边界）。语义同全参形态便捷版本
     * @param url 请求地址
     * @param params 普通表单字段，可空
     * @param parts 文件分片清单
     * @param boundary multipart 边界，空则自动生成
     * @return 返回语义由 error-mode 支配（见类注释）：throwout 抛 OkHttpStatusRuntimeException（非 2xx）
     *         或 OkHttpRuntimeException/IORuntimeException（IO 异常）；quiet/log 模式吞错——异常时返回空串、
     *         非 2xx 时返回错误体文本，<b>永不返回 null</b>，调用方须以返回值判成败
     */
    public String postFormDataPart(String url, Map<String, String> params, List<FormDataPart> parts, String boundary) {
        return postFormDataPart(url, params, null, parts, boundary);
    }

    /**
     * multipart 上传全参形态：表单字段 + 文件分片 + 请求头 + 自定义边界一次收齐。
     *
     * @param url      请求地址
     * @param params   普通表单字段，可空
     * @param headers  请求头，可空
     * @param parts    文件分片清单，可空
     * @param boundary multipart 边界，空则自动生成
     * @return 返回语义由 error-mode 支配（见类注释），吞错模式永不返回 null
     * @throws RuntimeException 任一分片的 file 为 null 或 mediaType 为空白时断言失败（前置校验，发起请求前抛出）
     */
    public String postFormDataPart(String url, Map<String, String> params, Map<String, String> headers, List<FormDataPart> parts, String boundary) {
        MultipartBody.Builder builder = StrUtil.isBlank(boundary) ? new MultipartBody.Builder() : new MultipartBody.Builder(boundary);
        if (CollectionUtil.isNotEmpty(params)) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                builder.addFormDataPart(entry.getKey(), entry.getValue());
            }
        }
        if (CollectionUtil.isNotEmpty(parts)) {
            for (FormDataPart part : parts) {
                Assert.notNull(part.getFile(), () -> new RuntimeException("part file must not be null"));
                Assert.notBlank(part.getMediaType(), () -> new RuntimeException("part mediaType must not be null"));
                builder.addFormDataPart(part.getName(), part.getFileName(), RequestBody.create(part.getFile(), MediaType.parse(part.getMediaType())));
            }
        }
        RequestBody body = builder.setType(MultipartBody.FORM).build();
        Request request = buildHeaders(headers).url(url).post(body).build();

        return execute(request);
    }

    /**
     * post 请求
     *
     * @param url     请求url地址
     * @param params  请求参数 map
     * @param headers 请求头字段 {k1:v1, k2: v2, ...}
     * @return string
     */
    public String post(String url, Map<String, String> params, Map<String, String> headers) {
        return post(url, params, headers, null);
    }

    /**
     * post 请求
     *
     * @param url
     * @param params
     * @param charset
     * @return
     */
    public String post(String url, Map<String, String> params, Charset charset) {
        return post(url, params, null, charset);
    }

    /**
     * post 请求
     *
     * @param url     请求url地址
     * @param params  请求参数 map
     * @param headers 请求头字段 {k1:v1, k2: v2, ...}
     * @return string
     */
    public String post(String url, Map<String, String> params, Map<String, String> headers, Charset charset) {
        Request request = buildHeaders(headers).url(url).post(buildFormBody(params, charset)).build();
        return execute(request, charset);
    }

    /**
     * post 请求, 请求数据为 json 的字符串
     *
     * @param url  请求url地址
     * @param json 请求数据, json 字符串
     * @return string
     */
    public String postJson(String url, String json) {
        return postJson(url, json, StandardCharsets.UTF_8);
    }

    /**
     * post 请求, 请求数据为 json 的字符串
     *
     * @param url     请求url地址
     * @param json    请求数据, json 字符串
     * @param headers 请求头字段 {k1, v1 k2, v2, ...}
     * @return string
     */
    public String postJson(String url, String json, Map<String, String> headers) {
        return postJson(url, json, headers, null);
    }

    /**
     * post 请求, 请求数据为 json 的字符串
     *
     * @param url
     * @param json
     * @param charset
     * @return
     */
    public String postJson(String url, String json, Charset charset) {
        return postJson(url, json, null, charset);
    }

    /**
     * post 请求, 请求数据为 json 的字符串
     *
     * @param url     请求url地址
     * @param json    请求数据, json 字符串
     * @param headers 请求头字段 {k1, v1 k2, v2, ...}
     * @return string
     */
    public String postJson(String url, String json, Map<String, String> headers, Charset charset) {
        RequestBody requestBody = RequestBody.create(json, charset != null ? MediaType.parse("application/json; charset=" + charset.name()) : JSON);
        Request request = buildHeaders(headers).url(url).post(requestBody).build();
        return execute(request, charset);
    }

    /**
     * post 请求, 请求数据为 xml 的字符串
     *
     * @param url 请求url地址
     * @param xml 请求数据, xml 字符串
     * @return string
     */
    public String postXml(String url, String xml) {
        return postXml(url, xml, null, null);
    }

    /**
     * post 请求, 请求数据为 xml 的字符串
     *
     * @param url
     * @param xml
     * @param headers
     * @return
     */
    public String postXml(String url, String xml, Map<String, String> headers) {
        return postXml(url, xml, headers, null);
    }

    /**
     * post 请求, 请求数据为 xml 的字符串
     *
     * @param url
     * @param xml
     * @param charset
     * @return
     */
    public String postXml(String url, String xml, Charset charset) {
        return postXml(url, xml, null, charset);
    }

    /**
     * 请求, 请求数据为 xml 的字符串
     *
     * @param url     请求地址
     * @param xml     xml 报文体
     * @param headers 请求头，可空
     * @param charset 一体两用：既写进 Content-Type 作请求体编码，也作为响应体解码字符集
     *                （经 execute 透传）——目标站响应编码与请求不一致时必须显式指定，
     *                null 则请求默认 utf-8、响应按 OkHttp 自动探测
     * @return 响应体文本；非 2xx 行为受 error-mode 配置支配（见类注释）
     */
    public String postXml(String url, String xml, Map<String, String> headers, Charset charset) {
        RequestBody requestBody = RequestBody.create(xml, charset != null ? MediaType.parse("application/xml; charset=" + charset.name()) : XML);
        Request request = buildHeaders(headers).url(url).post(requestBody).build();
        return execute(request, charset);
    }

    /**
    * 请求头写入 Builder（空表原样返回）。
    */
    private Request.Builder buildHeaders(Map<String, String> headers) {
        Request.Builder builder = new Request.Builder();
        if (CollectionUtil.isNotEmpty(headers)) {
            for (String header : headers.keySet()) {
                builder.addHeader(header, headers.get(header));
            }
        }
        return builder;
    }

     /**
     * GET 下载为字节数组。语义同 {@link #download(String, Map, Map)} 便捷形态
     * @param url 下载地址
     * @return 返回响应体字节；throwout 模式异常同上抛出；quiet/log 吞错时异常返回 <b>null</b>
     *          （注意与文本方法的"空串"差异，判空须用引用比较）
     */
    public byte[] download(String url) {
        return download(url, null, null);
    }

     /**
     * 带请求头 GET 下载。语义同 {@link #download(String, Map, Map)} 便捷形态
     * @param url 下载地址
     * @param headers 请求头，可空（鉴权类下载必带）
     * @return 返回响应体字节；throwout 模式异常同上抛出；quiet/log 吞错时异常返回 <b>null</b>
     *          （注意与文本方法的"空串"差异，判空须用引用比较）
     */
    public byte[] downloadWithHeaders(String url, Map<String, String> headers) {
        return download(url, null, headers);
    }

     /**
     * 带查询参数 GET 下载。语义同 {@link #download(String, Map, Map)} 便捷形态
     * @param url 下载地址
     * @param params 查询参数，拼入 URL
     * @return 返回响应体字节；throwout 模式异常同上抛出；quiet/log 吞错时异常返回 <b>null</b>
     *          （注意与文本方法的"空串"差异，判空须用引用比较）
     */
    public byte[] downloadWithParams(String url, Map<String, String> params) {
        return download(url, params, null);
    }

     /**
     * GET 下载全参形态（大文件建议改用 downloadAsStream 避免整包进内存）
     * @param url 下载地址
     * @param params 查询参数，可空
     * @param headers 请求头，可空
     * @return 返回响应体字节；throwout 模式异常同上抛出；quiet/log 吞错时异常返回 <b>null</b>
     *          （注意与文本方法的"空串"差异，判空须用引用比较）
     */
    public byte[] download(String url, Map<String, String> params, Map<String, String> headers) {
        return executeByte(prepareGetRequest(url, params, headers));
    }

     /**
     * GET 下载为流。语义同 {@link #downloadAsStream(String, Map, Map)} 便捷形态
     * @param url 下载地址
     * @return 返回响应体流，<b>所有权移交调用方</b>——必须自行 close（连带归还连接池连接）；
     *         quiet/log 吞错时异常返回 null
     */
    public InputStream downloadAsStream(String url) {
        return downloadAsStream(url, null, null);
    }

     /**
     * 带请求头 GET 下载为流。语义同 {@link #downloadAsStream(String, Map, Map)} 便捷形态
     * @param url 下载地址
     * @param headers 请求头，可空
     * @return 返回响应体流，<b>所有权移交调用方</b>——必须自行 close（连带归还连接池连接）；
     *         quiet/log 吞错时异常返回 null
     */
    public InputStream downloadWithHeadersAsStream(String url, Map<String, String> headers) {
        return downloadAsStream(url, null, headers);
    }

     /**
     * 带查询参数 GET 下载为流。语义同 {@link #downloadAsStream(String, Map, Map)} 便捷形态
     * @param url 下载地址
     * @param params 查询参数
     * @return 返回响应体流，<b>所有权移交调用方</b>——必须自行 close（连带归还连接池连接）；
     *         quiet/log 吞错时异常返回 null
     */
    public InputStream downloadWithParamsAsStream(String url, Map<String, String> params) {
        return downloadAsStream(url, params, null);
    }

     /**
     * GET 下载为流全参形态：Response 由内部包装持有，调用方 close 流时连带释放
     * @param url 下载地址
     * @param params 查询参数，可空
     * @param headers 请求头，可空
     * @return 返回响应体流，<b>所有权移交调用方</b>——必须自行 close（连带归还连接池连接）；
     *         quiet/log 吞错时异常返回 null
     */
    public InputStream downloadAsStream(String url, Map<String, String> params, Map<String, String> headers) {
        return executeStream(prepareGetRequest(url, params, headers));
    }

     /**
     * POST（表单）取字节：以表单参数发起 POST 并按二进制收取响应
     * @param url 请求地址
     * @param params 表单参数
     * @param headers 请求头，可空
     * @return 返回响应体字节；throwout 模式异常同上抛出；quiet/log 吞错时异常返回 <b>null</b>
     *          （注意与文本方法的"空串"差异，判空须用引用比较）
     */
    public byte[] postDownloadAsByte(String url, Map<String, String> params, Map<String, String> headers) {
        Request request = buildHeaders(headers).url(url).post(buildFormBody(params, null)).build();
        return executeByte(request);
    }

     /**
     * POST（表单）下载为流：所有权语义同 GET 流形态
     * @param url 请求地址
     * @param params 表单参数
     * @param headers 请求头，可空
     * @return 返回响应体流，<b>所有权移交调用方</b>——必须自行 close（连带归还连接池连接）；
     *         quiet/log 吞错时异常返回 null
     */
    public InputStream postDownloadAsStream(String url, Map<String, String> params, Map<String, String> headers) {
        Request request = buildHeaders(headers).url(url).post(buildFormBody(params, null)).build();
        return executeStream(request);
    }

    /**
    * 文本执行便捷形态（charset=null，body().string() 语义）。
    */
    private String execute(Request request) {
        return execute(request, null);
    }

    /**
    * 文本执行核心：读取响应（charset 指定解码）→ 非 2xx 按 error-mode 分派 → 异常 processError。
    */
    private String execute(Request request, Charset charset) {
        log(request);
        try (Response response = getClient().newCall(request).execute()) {
            if (response.body() != null) {
                String body = charset != null ? new String(response.body().bytes(), charset) : response.body().string();
                if (!response.isSuccessful() && properties.getErrorMode() == OkhttpProperties.ErrorMode.throwout) {
                    // BUG-3 修复：非 2xx 统一尊重 error-mode——throwout 抛带响应信息的状态异常
                    //（此处抛出会被本方法 catch 捕获并经 processError 在 throwout 分支原样重抛）；
                    // quiet/log 模式返回错误体（与原 GET 路径行为一致，两条路径自此对称）。
                    // 错误体已随 body 解出，直接复用变量，不做二次读取
                    OkHttpStatusRuntimeException.ofThrow(response.code(), response.message(),
                            response.request().url().toString(), body);
                }
                return body;
            }
        } catch (Exception e) {
            processError(e);
        }
        return StrUtil.EMPTY;
    }


    /**
    * 字节执行核心：2xx 且有体读字节返回；非 2xx（或空体）按 error-mode 分派 → 异常 processError。
    * 非 2xx 与文本路径同语义：throwout 抛 OkHttpStatusRuntimeException，quiet/log 吞错；
    * 吞错路径返回 null（文本方法返回空串，差异见各公开方法注释）。
    */
    private byte[] executeByte(Request request) {
        log(request);
        try (Response response = getClient().newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().bytes();
            }
            // 非 2xx 统一 error-mode 分派：throwout 抛状态异常（错误体在此安静读出供异常携带），
            // quiet/log 不动作；try-with-resources 保证两种模式都正确关闭响应
            throwIfNon2xxOnThrowout(response);
        } catch (Exception e) {
            processError(e);
        }
        return null;
    }

    /**
    * 流执行核心：2xx 且有体将 Response 包装移交调用方（所有权语义见 ResponseBodyStreamWrapper）；
    * 非 2xx（或空体）按 error-mode 分派 → 异常 processError。吞错路径关闭响应后返回 null。
    */
    private InputStream executeStream(Request request) {
        log(request);
        try {
            Response response = getClient().newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                // 流包装接管 Response 的关闭职责：调用方读完/关闭流时连带关闭 Response（连接归还连接池），
                // 未读完即 close 也能正确释放
                return new ResponseBodyStreamWrapper(response);
            }
            // 非 2xx 统一 error-mode 分派：throwout 抛状态异常、quiet/log 吞错返回 null。
            // finally 兜底关闭响应——抛出路径也不泄漏连接（错误体已安静耗尽，close 为幂等收尾）
            try {
                throwIfNon2xxOnThrowout(response);
            } finally {
                response.close();
            }
        } catch (Exception e) {
            processError(e);
        }
        return null;
    }

    /**
    * 请求打点日志。
    */
    private void log(Request request) {
        log.info("do request [{}]", request.url());
    }

    /**
    * BUG-3 修复后委托 execute（消除姊妹方法错误体语义不对称）。
    */
    private String executeBody(Request request) {
        // BUG-3 修复：原实现是 execute 的姊妹方法但无 isSuccessful 分支（错误体当正常串返回），
        // 现直接委托 execute（charset=null 即 body().string() 语义），从根上消除姊妹方法不对称
        return execute(request, null);
    }

    /**
     * 响应体流包装：接管 Response 的关闭职责。
     * 原实现把 body 流从 try-with-resources 内返回，Response 随块结束关闭，调用方拿到死流。
     * 现由本包装持有 Response，调用方读完或 close 流时连带关闭 Response（连接归还连接池）。
     */
    private static final class ResponseBodyStreamWrapper extends java.io.FilterInputStream {
        private final Response response;

        ResponseBodyStreamWrapper(Response response) {
            super(response.body().byteStream());
            this.response = response;
        }

        /**
        * 关闭流并 finally 连带关闭持有的 Response（流所有权移交的实现点，见外层类注释）。
        * @throws java.io.IOException 底层流关闭异常
        */
        public void close() throws java.io.IOException {
            try {
                super.close();
            } finally {
                response.close();
            }
        }
    }

    /**
    * 非 2xx 统一 error-mode 分派点（字节/流路径共用）：throwout 抛 OkHttpStatusRuntimeException
    * （携带 code/message/url 与安静读出的错误体；经 processError throwout 分支原样重抛，类型不降级）；
    * quiet/log 不做任何动作，由调用方按各自路径返回 null。
    * 文本路径（execute）同语义分派内联在读取后——错误体已解出直接复用，不走本方法以免二次读体。
    */
    private void throwIfNon2xxOnThrowout(Response response) {
        if (!response.isSuccessful() && properties.getErrorMode() == OkhttpProperties.ErrorMode.throwout) {
            OkHttpStatusRuntimeException.ofThrow(response.code(), response.message(),
                    response.request().url().toString(), readBodyQuietly(response));
        }
    }

    /**
    * 安静读取响应体文本（响应无体或读取 IO 失败时降级为空串），仅供异常上下文携带错误详情，
    * 读取出错不影响主异常的抛出。
    */
    private String readBodyQuietly(Response response) {
        try {
            return response.body() != null ? response.body().string() : StrUtil.EMPTY;
        } catch (IOException ignored) {
            return StrUtil.EMPTY;
        }
    }

    /**
    * error-mode 分派：quiet 无动作 / log_simple 简单日志 / log_detail 详细日志 / throwout 按异常类型重抛。
    */
    private void processError(Throwable e) {
        OkhttpProperties.ErrorMode errorMode = properties.getErrorMode();
        switch (errorMode) {
            case log_simple:
                log.error(ExceptionUtil.getMessage(e));
                break;
            case log_detail:
                log.error(ExceptionUtil.getMessage(e), e);
                break;
            case throwout:
                if (e instanceof OkHttpStatusRuntimeException) {
                    // 非 2xx 状态异常原样重抛：保住 code/url/body 访问器语义，不被包装降级
                    throw (OkHttpStatusRuntimeException) e;
                } else if( e instanceof OkHttpRuntimeException){
                    throw (OkHttpRuntimeException) e;
                } else if(e instanceof IOException){
                    IORuntimeException.ofThrow(e);
                } else {
                    OkHttpRuntimeException.ofThrow(e);
                }
                break;
            default:
                break;

        }
    }

     /**
     * 取重定向目标地址：探测目标 URL，3xx 且携带非空 Location 头时返回该地址
     * @param url 探测地址
     * @return 重定向目标（Location 头）；<b>非重定向、Location 缺失或吞错模式下异常时原样返回入参 url</b>（不返回 null）；throwout 模式异常照抛
     */
    public String getRedirectUrl(String url) {
        Request request = new Request.Builder().url(url).build();
        try (Response response = getClient().newCall(request).execute()) {
            if (response.isRedirect()) {
                String location = response.header("Location");
                if (StrUtil.isNotBlank(location)) {
                    return location;
                }
            }
        } catch (IOException e) {
            processError(e);
        }
        return url;
    }
//    public <R> R withProxy(CallbackPR<OkhttpUtil, R> callback) {
//        PROXY_MODE.set(Boolean.TRUE);
//        try {
//            return callback.call(of());
//        } finally {
//            PROXY_MODE.remove();
//        }
//    }
//
//    public void withProxy(CallbackP<OkhttpUtil> callback) {
//        PROXY_MODE.set(Boolean.TRUE);
//        try {
//            callback.call(of());
//        } finally {
//            PROXY_MODE.remove();
//        }
//    }

//    /**
//     * 获取okhttp client
//     * 注意：本方法获取的client 不支持host代理绕过
//     *
//     * @return
//     */
//    public OkHttpClient getClient() {
//        return getClient(null);
//    }

    /**
     * 取当前生效客户端：customOkhttpClient（ofProxy 定制）优先，其次按 proxy-mode 选代理/直连客户端
     *
     * @return 当前生效的 OkHttpClient 实例（装配期保证非 null）
     */
    public OkHttpClient getClient() {
        if (this.customOkhttpClient != null) {
            return this.customOkhttpClient;
        }

        if (properties.getProxyMode().equals(OkhttpProperties.ProxyMode.always)) {
            return this.okHttpProxyClient;
        }
        return this.okHttpClient;
    }
//
//    /**
//     * 代理绕过判断，如果匹配则该host请求默认使用非代理客户端
//     *
//     * @param host
//     * @return
//     */
//    public boolean isProxyIgnored(String host) {
//        boolean isIgnore = false;
//        AntPathMatcher antPathMatcher = new AntPathMatcher();
//        // 代理绕过
//        for (String ignoreHost : this.properties.getProxyIgnores()) {
//            if (antPathMatcher.match(ignoreHost, host)) {
//                isIgnore = true;
//                log.debug("ignore used proxy host[{}][{}][{}]", host, ignoreHost, isIgnore);
//                break;
//            }
//        }
//        return isIgnore;
//    }

     /**
     * 取容器装配的门面单例（默认客户端，proxy-mode 决定直连还是代理）。
     * 优先消费 ContextRefreshedEvent 提升的静态引用（刷新完成后零容器查找），
     * 未发布（上下文刷新窗口内被调用）时回落 SpringUtil.getBean 原语义
     * @return 容器中的 OkHttpUtil 单例
     */
    public static OkHttpUtil of() {
        return containerFacade();
    }

    /**
     * 使用自定义客户端
     *
     * @param client
     * @return
     */
    public static OkHttpUtil of(OkHttpClient client) {
        OkHttpUtil bean = containerFacade().clone();
        bean.customOkhttpClient = client;
        return bean;
    }

    /**
     * 依据配置文件自动初始化基础配置（不含代理）并提供自定义配置渠道
     *
     * @param config
     * @return
     */
    public static OkHttpUtil of(CustomOkhttpConfiguration config) {
        OkHttpUtil bean = containerFacade().clone();
        // configuration/pool 为定制构建期依赖：定制客户端只发生在刷新完成后的业务线程
        // （遗留 #1 复核定性：无刷新窗口的外部线程调用路径，不构成 DEF-1 等待环，保持运行期查找）
        JevokitOkhttpConfiguration configuration = SpringUtil.getBean(JevokitOkhttpConfiguration.class);
        ConnectionPool pool = SpringUtil.getBean(ConnectionPool.class);
        //初始化非代理客户端
        OkHttpClient.Builder nonProxyBuilder = configuration.createBasicBuilder(pool);
        config.configClient(nonProxyBuilder);

        //初始化代理客户端
        OkHttpClient.Builder proxyBuilder = configuration.createBasicBuilder(pool);
        config.configProxyClient(nonProxyBuilder);
        configuration.initProxy(proxyBuilder);

        bean.okHttpClient = nonProxyBuilder.build();
        bean.okHttpProxyClient = proxyBuilder.build();
        return bean;
    }

     /**
     * 克隆门面并定制代理客户端：以基础 Builder + 代理初始化后经 config 二次改写，得到独立副本（不影响单例）
     * @param config 代理客户端定制回调（超时/拦截器等）
     * @return 携带 customOkhttpClient 的新门面副本，后续调用全部走该定制客户端
     */
    public static OkHttpUtil ofProxy(CustomOkhttpProxyConfiguration config) {
        OkHttpUtil bean = containerFacade().clone();
        // 定制构建期依赖保持运行期查找（定性同 of(CustomOkhttpConfiguration)，遗留 #1 复核）
        JevokitOkhttpConfiguration configuration = SpringUtil.getBean(JevokitOkhttpConfiguration.class);
        ConnectionPool pool = SpringUtil.getBean(ConnectionPool.class);
        OkHttpClient.Builder basicBuilder = configuration.createBasicBuilder(pool);
        configuration.initProxy(basicBuilder);
        config.configClient(basicBuilder);
        bean.customOkhttpClient = basicBuilder.build();
        return bean;
    }

     /**
     * 克隆门面并强制切到代理客户端（manual 模式下的显式走代理入口，不影响单例）
     * @return 强制使用代理客户端的新门面副本
     */
    public static OkHttpUtil ofProxy() {
        OkHttpUtil bean = containerFacade().clone();
        bean.customOkhttpClient = bean.okHttpProxyClient;
        return bean;
    }

    /**
     * 取门面单例：静态引用（ContextRefreshedEvent 提升）优先，未发布回落 SpringUtil.getBean。
     * 回落仅发生在上下文刷新窗口内的调用（常态为业务线程在刷新完成后调用，静态引用必已发布）。
     */
    private static OkHttpUtil containerFacade() {
        OkHttpUtil facade = staticInstance;
        return facade != null ? facade : SpringUtil.getBean(OkHttpUtil.class);
    }

    /** 上下文刷新完成回调：把容器门面单例提升为静态引用（of() 家族此后零容器查找）。
     *  经容器取而非记 this，与容器单例（含后续克隆源）保持同一实例。 */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        staticInstance = event.getApplicationContext().getBean(OkHttpUtil.class);
    }

    /** 上下文关闭回调：清理静态引用（与发布配对，防 spring-test 多上下文/热重启残留——
     *  与 DesHelper 同定式，遗留 #1+#2 统一）。清理后 of() 回落 getBean 原语义，行为不变；
     *  多上下文为"最后刷新者胜出、任一关闭即清理"（静态字段进程级单值，与发布侧一致）。 */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed(ContextClosedEvent event) {
        staticInstance = null;
    }

    /**
    * 表单体构建（charset 进 FormBody 编码）。
    */
    private static FormBody buildFormBody(Map<String, String> params, Charset charset) {
        FormBody.Builder builder = charset != null ? new FormBody.Builder(charset) : new FormBody.Builder();
        if (CollectionUtil.isNotEmpty(params)) {
            for (String key : params.keySet()) {
                builder.add(key, params.get(key));
            }
        }
        return builder.build();
    }

    /**
     * 转化参数字符串
     *
     * @param params
     * @param charset
     * @return
     */
    public static Map<String, String> parseParams(String params, Charset charset) {

        if (StrUtil.isNotBlank(params)) {
            return HttpUtil.decodeParamMap(params, charset);
        }
        return new HashMap<>();
    }

    /**
     * 转化参数字符串
     *
     * @param params
     * @return
     */
    public static Map<String, String> parseParams(String params) {
        return parseParams(params, StandardCharsets.UTF_8);
    }


    /**
     * 构建get请求url
     *
     * @param url
     * @param params
     * @return
     */
    @Deprecated
    private static String buildGetUrl(String url, Map<String, String> params) {
        return buildUrlByQueryParams(url,params,true,StandardCharsets.UTF_8);
    }
    /**
     * 构建请求url
     * @param url
     * @param params
     * @return
     */
    public static String buildUrlByQueryParams(String url, Map<String, String> params) {
        return buildUrlByQueryParams(url,params,true,StandardCharsets.UTF_8);
    }
    /**
     * 构建请求url
     * @param url
     * @param params
     * @return
     */
    public static String buildUrlByQueryParams(String url, Map<String, String> params,boolean urlEncode,Charset charset) {
        StringBuilder sb = new StringBuilder(url);
        if (CollectionUtil.isNotEmpty(params)) {
            boolean firstFlag = true;
            for (String key : params.keySet()) {
                if (firstFlag) {
                    sb.append("?");
                    firstFlag = false;
                } else {
                    sb.append("&");
                }

                if(urlEncode){
                    try {
                        sb.append(URLEncoder.encode(key,charset.name())).append("=").append(URLEncoder.encode(params.get(key),charset.name()));
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }

                } else {
                    sb.append(key).append("=").append(params.get(key));
                }
            }
        }
        return sb.toString();
    }

    /**
     * 取真实客户端 IP（含多级代理场景），委托 HttpUtil 实现。
     *
     * @param request 当前请求
     * @return 直连或经代理时的真实客户端 IP（取值优先级见 HttpUtil）
     * @deprecated use HttpUtil.getRemoteIp instead
     */
    @Deprecated
    public static String getRemoteIp(HttpServletRequest request) {
        return HttpUtil.getRemoteIp(request);
    }

    /**
     * 请求头构造入口：headerBuilder().add(k, v)...build() 链式产出 Map，
     * 供本类各 *WithHeaders / headers 形态使用。
     *
     * @return 新的 HeaderBuilder 实例（无共享状态）
     */
    public static HeaderBuilder headerBuilder() {
        return new HeaderBuilder();
    }

}
