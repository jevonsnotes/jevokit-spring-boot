package io.github.jevonsnotes.jevokit.api.context;

import org.springframework.web.bind.MethodArgumentNotValidException;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.consts.ApiError;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;

import java.lang.reflect.Method;

/**
 * JSecure 协议的请求线程上下文：以 ThreadLocal 暂存当前请求的协议报文、接入终端配置与日志前缀，
 * 供解密适配器、业务处理与异常兜底在同请求内随时取用。
 * 使用方式：服务端业务一般只读（{@link #getRequest()} / {@link #getCurrentClientThrowIfInvalid()}）；
 * 写入与清理由协议组件负责（解密适配器写入，拦截器 afterCompletion 调 {@link #remove()}）。
 * <p>关键约束：
 * <ul>
 *   <li>getCurrentClient 惰性解析（按请求头终端识别码匹配 client 配置）并以 ThreadLocal 缓存，同请求只解析一次；</li>
 *   <li>终端不存在或 enabled=false 时 {@link #getCurrentClientThrowIfInvalid()} 抛 {@link JSecureApiException}，
 *       防止未登记终端走到解密逻辑；</li>
 *   <li>必须成对 remove，否则线程池复用会串请求（异步场景由拦截器的并发回调兜底清理）。</li>
 * </ul>
 */
public class JSecureContext {
    public static final ThreadLocal<JSecureRequest<?>> JS_REQUEST_THREAD_LOCAL = new ThreadLocal<>();
    public static final ThreadLocal<JevokitApiClientProperties> CLIENT_THREAD_LOCAL = new ThreadLocal<>();
    public static final ThreadLocal<String> LOG_PREFIX = new ThreadLocal<>();

    public static void setRequest(final JSecureRequest<?> request) {
        JS_REQUEST_THREAD_LOCAL.set(request);
    }
    public static void setLogPrefix(final String logPrefix) {
        LOG_PREFIX.set(logPrefix);
    }
    public static String getLogPrefix() {
        return LOG_PREFIX.get();
    }
    public static JSecureRequest<?> getRequest() {
        return JS_REQUEST_THREAD_LOCAL.get();
    }

    public static JevokitApiClientProperties getCurrentClient() {
        JevokitApiClientProperties currentClient = CLIENT_THREAD_LOCAL.get();
        if (currentClient == null) {
            currentClient = JSecureApiUtil.getCurrentClient();
            if(currentClient != null) {
                CLIENT_THREAD_LOCAL.set(currentClient);
            }
        }
        return currentClient;
    }

    public static JevokitApiClientProperties getCurrentClientThrowIfInvalid() {
        JevokitApiClientProperties currentClient = getCurrentClient();
        if(currentClient == null || !currentClient.isEnabled()) {
            throw new JSecureApiException(ApiError.FAIL.code(), "终端信息不存在或已关闭访问策略");
        }
        return currentClient;
    }


    public static void remove() {
        JS_REQUEST_THREAD_LOCAL.remove();
        CLIENT_THREAD_LOCAL.remove();
        LOG_PREFIX.remove();
    }


    public static boolean isMethodArgumentNotValidException(Exception e) {
        if(e instanceof MethodArgumentNotValidException) {
            Method method = ((MethodArgumentNotValidException) e).getParameter().getMethod();
            if(method != null){
                Class<?> returnType = method.getReturnType();
                return returnType == JSecureResponse.class;
            }
        }
        return false;
    }
}
