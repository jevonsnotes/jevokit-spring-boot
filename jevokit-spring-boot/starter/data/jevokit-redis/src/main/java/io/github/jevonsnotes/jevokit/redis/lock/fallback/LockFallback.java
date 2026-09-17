package io.github.jevonsnotes.jevokit.redis.lock.fallback;

import org.aspectj.lang.reflect.MethodSignature;

 /**
 * 锁占用降级契约：@Lock 命中并发锁时的替代返回逻辑，signature 携带被拦截方法签名
 * 供反射取返回类型/注解。缺省实现见 DefaultLockFallback（返回 fallbackStr 文案）。
 */
public interface LockFallback<T> {
    /**
    * 锁占用降级契约实现点。
    * @param signature 被拦截方法签名
    * @param args 被拦截方法入参
    * @return 降级返回值（形态由实现自定）
    */
    T fallback(MethodSignature signature, Object... args);
}
