package io.github.jevonsnotes.jevokit.redis.lock.fallback;

import org.aspectj.lang.reflect.MethodSignature;
import io.github.jevonsnotes.jevokit.redis.lock.annotation.Lock;

/**
 * @Description TODO
 * @Date 2022/7/21 17:28
 * @Version 0.0.1
 */
public class DefaultLockFallback implements LockFallback<String>{
    @Override
    public String fallback(MethodSignature signature, Object... args) {
        return signature.getMethod().getAnnotation(Lock.class).fallbackStr();
    }
}
