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

    /** 记录当前请求的协议报文（解密适配器写入，业务只读）。 */
    public static void setRequest(final JSecureRequest<?> request) {
        JS_REQUEST_THREAD_LOCAL.set(request);
    }
    /** 设置协议链路日志前缀（形如 JSecureApi 类#方法，便于检索同请求日志）。 */
    public static void setLogPrefix(final String logPrefix) {
        LOG_PREFIX.set(logPrefix);
    }
    /** 获取日志前缀；未设置时为 null（调用方自行兜底）。 */
    public static String getLogPrefix() {
        return LOG_PREFIX.get();
    }
    /** 获取当前请求协议报文（含解密后的 view/target）；非协议请求为 null。 */
    public static JSecureRequest<?> getRequest() {
        return JS_REQUEST_THREAD_LOCAL.get();
    }

    /**
     * 获取当前终端配置（ThreadLocal 缓存 + 惰性解析，判定语义见 JSecureApiUtil#getCurrentClient）。
     *
     * @return 命中的终端；未命中/无上下文返回 null
     */
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

    /**
     * 获取当前终端并校验有效性。
     *
     * @return 终端配置（保证非 null 且 enabled=true）
     * @throws JSecureApiException 终端不存在或已停用时抛出（拒绝未登记终端进入协议处理）
     */
    public static JevokitApiClientProperties getCurrentClientThrowIfInvalid() {
        JevokitApiClientProperties currentClient = getCurrentClient();
        if(currentClient == null || !currentClient.isEnabled()) {
            throw new JSecureApiException(ApiError.FAIL.code(), "终端信息不存在或已关闭访问策略");
        }
        return currentClient;
    }


    /** 清理全部 ThreadLocal（拦截器在请求结束/异常时调用，必须成对执行防线程池串请求）。 */
    public static void remove() {
        JS_REQUEST_THREAD_LOCAL.remove();
        CLIENT_THREAD_LOCAL.remove();
        LOG_PREFIX.remove();
    }


    /**
     * 判定异常是否为 JSecure 协议方法的参数校验失败（供全局异常处理区分兜底形态）。
     *
     * @param e 待判定异常
     * @return true 表示该异常来自返回 JSecureResponse 的方法的 @Valid 校验
     */
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
