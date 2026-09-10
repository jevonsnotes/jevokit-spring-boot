package springbootkit.okhttp.util;

import cn.hutool.core.clone.CloneSupport;
import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import springbootkit.core.exception.IORuntimeException;
import springbootkit.core.exception.OkHttpRuntimeException;
import springbootkit.core.exception.OkHttpStatusRuntimeException;
import springbootkit.core.util.CollectionUtil;
import springbootkit.core.util.HttpUtil;
import springbootkit.okhttp.config.OkhttpProperties;
import springbootkit.okhttp.config.SpringBootKitOkhttpConfiguration;
import springbootkit.okhttp.dto.FormDataPart;
import springbootkit.okhttp.interceptor.CustomOkhttpConfiguration;
import springbootkit.okhttp.interceptor.CustomOkhttpProxyConfiguration;
import springbootkit.okhttp.lang.HeaderBuilder;

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
 * @Description TODO
 * @Date 2024/8/28
 * @Version 0.0.1
 */

@Slf4j
public class OkHttpUtil extends CloneSupport<OkHttpUtil> {
    private final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final MediaType XML = MediaType.parse("application/xml; charset=utf-8");
    private OkHttpClient okHttpClient;
    private OkHttpClient okHttpProxyClient;
    private final OkhttpProperties properties;
    //    private final ThreadLocal<Boolean> PROXY_MODE = new ThreadLocal<>();
    private OkHttpClient customOkhttpClient;


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

    public byte[] getByte(String url, Map<String, String> params, Map<String, String> headers) {
        return executeByte(prepareGetRequest(url, params, headers));
    }

    private Request prepareGetRequest(String url, Map<String, String> params, Map<String, String> headers) {
        return buildHeaders(headers).url(buildUrlByQueryParams(url, params)).build();
    }


    public String post(String url) {
        return post(url, null, StandardCharsets.UTF_8);
    }

    public String post(String url, Charset charset) {
        return post(url, null, charset);
    }

    public String post(String url, Map<String, String> params) {
        return post(url, params, StandardCharsets.UTF_8);
    }

    public String postBody(String url, String body, String contentType, Charset charset) {
        return postBody(url, body, null, contentType, charset);
    }

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

    public String postFormDataPart(String url, List<FormDataPart> parts) {
        return postFormDataPart(url, null, null, parts, null);
    }

    public String postFormDataPart(String url, Map<String, String> params, List<FormDataPart> parts) {
        return postFormDataPart(url, params, null, parts, null);
    }

    public String postFormDataPart(String url, Map<String, String> params, FormDataPart part) {
        return postFormDataPart(url, params, null, CollectionUtil.toList(part), null);
    }

    public String postFormDataPart(String url, Map<String, String> params, FormDataPart part, String boundary) {
        return postFormDataPart(url, params, null, CollectionUtil.toList(part), boundary);
    }

    public String postFormDataPart(String url, Map<String, String> params, List<FormDataPart> parts, String boundary) {
        return postFormDataPart(url, params, null, parts, boundary);
    }

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
     * @param url
     * @param xml
     * @param headers
     * @param charset
     * @return
     */
    public String postXml(String url, String xml, Map<String, String> headers, Charset charset) {
        RequestBody requestBody = RequestBody.create(xml, charset != null ? MediaType.parse("application/xml; charset=" + charset.name()) : XML);
        Request request = buildHeaders(headers).url(url).post(requestBody).build();
        return execute(request, charset);
    }

    private Request.Builder buildHeaders(Map<String, String> headers) {
        Request.Builder builder = new Request.Builder();
        if (CollectionUtil.isNotEmpty(headers)) {
            for (String header : headers.keySet()) {
                builder.addHeader(header, headers.get(header));
            }
        }
        return builder;
    }

    public byte[] download(String url) {
        return download(url, null, null);
    }

    public byte[] downloadWithHeaders(String url, Map<String, String> headers) {
        return download(url, null, headers);
    }

    public byte[] downloadWithParams(String url, Map<String, String> params) {
        return download(url, params, null);
    }

    public byte[] download(String url, Map<String, String> params, Map<String, String> headers) {
        return executeByte(prepareGetRequest(url, params, headers));
    }

    public InputStream downloadAsStream(String url) {
        return downloadAsStream(url, null, null);
    }

    public InputStream downloadWithHeadersAsStream(String url, Map<String, String> headers) {
        return downloadAsStream(url, null, headers);
    }

    public InputStream downloadWithParamsAsStream(String url, Map<String, String> params) {
        return downloadAsStream(url, params, null);
    }

    public InputStream downloadAsStream(String url, Map<String, String> params, Map<String, String> headers) {
        return executeStream(prepareGetRequest(url, params, headers));
    }

    public byte[] postDownloadAsByte(String url, Map<String, String> params, Map<String, String> headers) {
        Request request = buildHeaders(headers).url(url).post(buildFormBody(params, null)).build();
        return executeByte(request);
    }

    public InputStream postDownloadAsStream(String url, Map<String, String> params, Map<String, String> headers) {
        Request request = buildHeaders(headers).url(url).post(buildFormBody(params, null)).build();
        return executeStream(request);
    }

    private String execute(Request request) {
        return execute(request, null);
    }

    private String execute(Request request, Charset charset) {
        log(request);
        try (Response response = getClient().newCall(request).execute()) {
            checkStatus(response);
            if (response.isSuccessful() && response.body() != null) {
                return charset != null ? new String(response.body().bytes(), charset) : response.body().string();
            } else if (!response.isSuccessful() && response.body() != null) {
                log.warn(charset != null ? new String(response.body().bytes(), charset) : response.body().string());
            }
        } catch (Exception e) {
            processError(e);
        }
        return StrUtil.EMPTY;
    }


    private byte[] executeByte(Request request) {
        log(request);
        try (Response response = getClient().newCall(request).execute()) {
            checkStatus(response);
            if (response.isSuccessful() && response.body() != null) {
                return response.body().bytes();
            }
        } catch (Exception e) {
            processError(e);
        }
        return null;
    }

    private InputStream executeStream(Request request) {
        log(request);
        try (Response response = getClient().newCall(request).execute()) {
            checkStatus(response);
            if (response.isSuccessful() && response.body() != null) {
                return response.body().byteStream();
            }
        } catch (Exception e) {
            processError(e);
        }
        return null;
    }

    private void log(Request request) {
        log.info("do request [{}]", request.url());
    }

    private String executeBody(Request request) {
        log(request);
        try (Response response = getClient().newCall(request).execute()) {
            checkStatus(response);
            if (response.body() != null) {
                return response.body().string();
            }
        } catch (Exception e) {
            processError(e);
        }
        return StrUtil.EMPTY;
    }

    private void checkStatus(Response response){
        if(!response.isSuccessful() && properties.isHttpStatusSensitive()){
            String body = StrUtil.EMPTY;
            if (response.body() != null) {
                try {
                    body = response.body().string();
                } catch (IOException ignored) {
                }
            }
            OkHttpStatusRuntimeException.ofThrow(response.code(),response.message(),response.request().url().toString(),body);
        }
    }

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
                if( e instanceof OkHttpRuntimeException){
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
     * 使用默认客户端
     * 默认客户端有两种，一种是含代理服务器配置的客户端，一种是普通客户端，依据配置自动选择使用
     *
     * @return
     */
    public static OkHttpUtil of() {
        return SpringUtil.getBean(OkHttpUtil.class);
    }

    /**
     * 使用自定义客户端
     *
     * @param client
     * @return
     */
    public static OkHttpUtil of(OkHttpClient client) {
        OkHttpUtil bean = SpringUtil.getBean(OkHttpUtil.class).clone();
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
        OkHttpUtil bean = SpringUtil.getBean(OkHttpUtil.class).clone();
        SpringBootKitOkhttpConfiguration configuration = SpringUtil.getBean(SpringBootKitOkhttpConfiguration.class);
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
     * 依据配置文件自动初始化基础配置（含代理）并提供自定义配置渠道
     *
     * @param config
     * @return
     */
    public static OkHttpUtil ofProxy(CustomOkhttpProxyConfiguration config) {
        OkHttpUtil bean = SpringUtil.getBean(OkHttpUtil.class).clone();
        SpringBootKitOkhttpConfiguration configuration = SpringUtil.getBean(SpringBootKitOkhttpConfiguration.class);
        ConnectionPool pool = SpringUtil.getBean(ConnectionPool.class);
        OkHttpClient.Builder basicBuilder = configuration.createBasicBuilder(pool);
        configuration.initProxy(basicBuilder);
        config.configClient(basicBuilder);
        bean.customOkhttpClient = basicBuilder.build();
        return bean;
    }

    /**
     * 使用代理客户端
     *
     * @return
     */
    public static OkHttpUtil ofProxy() {
        OkHttpUtil bean = SpringUtil.getBean(OkHttpUtil.class).clone();
        bean.customOkhttpClient = bean.okHttpProxyClient;
        return bean;
    }

    private static FormBody buildFormBody(Map<String, String> params, Charset charset) {
        FormBody.Builder builder = new FormBody.Builder();
        if (CollectionUtil.isNotEmpty(params)) {
            for (String key : params.keySet()) {
                String value = params.get(key);
                if (charset != null) {
                    value = StrUtil.str(value.getBytes(), charset);
                }
                builder.add(key, value);
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
     * 获取请求主机IP地址,如果通过代理进来，则透过防火墙获取真实IP地址
     *
     * @param request
     * @return
     * @deprecated use HttpUtil.getRemoteIp instead
     */
    @Deprecated
    public static String getRemoteIp(HttpServletRequest request) {
        return HttpUtil.getRemoteIp(request);
    }

    public static HeaderBuilder headerBuilder() {
        return new HeaderBuilder();
    }

}
