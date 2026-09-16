package springbootkit.api.context;

import org.springframework.web.bind.MethodArgumentNotValidException;
import springbootkit.api.config.SpringBootKitApiClientProperties;
import springbootkit.api.consts.ApiError;
import springbootkit.api.dto.JSecureRequest;
import springbootkit.api.dto.JSecureResponse;
import springbootkit.api.exception.JSecureApiException;
import springbootkit.api.util.JSecureApiUtil;

import java.lang.reflect.Method;

/**
 * @Description TODO
 * @Date 2025/4/15
 * @Version 0.0.1
 */
public class JSecureContext {
    public static final ThreadLocal<JSecureRequest<?>> JS_REQUEST_THREAD_LOCAL = new ThreadLocal<>();
    public static final ThreadLocal<SpringBootKitApiClientProperties> CLIENT_THREAD_LOCAL = new ThreadLocal<>();
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

    public static SpringBootKitApiClientProperties getCurrentClient() {
        SpringBootKitApiClientProperties currentClient = CLIENT_THREAD_LOCAL.get();
        if (currentClient == null) {
            currentClient = JSecureApiUtil.getCurrentClient();
            if(currentClient != null) {
                CLIENT_THREAD_LOCAL.set(currentClient);
            }
        }
        return currentClient;
    }

    public static SpringBootKitApiClientProperties getCurrentClientThrowIfInvalid() {
        SpringBootKitApiClientProperties currentClient = getCurrentClient();
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
