package springbootkit.redis.lock.fallback;

import org.aspectj.lang.reflect.MethodSignature;

/**
 * @Description TODO
 * @Date 2022/7/21 16:50
 * @Version 0.0.1
 */
public interface LockFallback<T> {
    /**
     *
     * @param args 被拦截方法的所有入参
     * @return
     */
    T fallback(MethodSignature signature, Object... args);
}
