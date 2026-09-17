package io.github.jevonsnotes.jevokit.redis.lock.fallback;

import org.aspectj.lang.reflect.MethodSignature;
import io.github.jevonsnotes.jevokit.redis.lock.annotation.Lock;

 /**
 * 缺省降级实现：直接返回 @Lock.fallbackStr 静态文案（不感知入参），
 * 即"请求处理中"类个性提示语。需要回传业务数据时换 AutoFetchParamFallback 或自实现。
 */
public class DefaultLockFallback implements LockFallback<String>{
    @Override
    /**
    * 返回 @Lock.fallbackStr 静态文案（@Key.print=true 时追加键值）。
    * @param signature 被拦截方法签名
    * @param args 入参（未使用）
    * @return 降级提示文案
    */
    public String fallback(MethodSignature signature, Object... args) {
        return signature.getMethod().getAnnotation(Lock.class).fallbackStr();
    }
}
