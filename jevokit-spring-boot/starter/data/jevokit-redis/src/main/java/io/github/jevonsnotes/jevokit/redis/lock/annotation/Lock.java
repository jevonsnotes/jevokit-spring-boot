package io.github.jevonsnotes.jevokit.redis.lock.annotation;

import io.github.jevonsnotes.jevokit.redis.lock.aspect.LockAspect;
import io.github.jevonsnotes.jevokit.redis.lock.fallback.DefaultLockFallback;
import io.github.jevonsnotes.jevokit.redis.lock.fallback.LockFallback;

import java.lang.annotation.*;

/**
 * @Description 方法级分布式锁
 * @Date 2022/7/21 16:42
 * @Version 0.0.1
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Lock {
    String DEFAULT_LOCK_FAILED_STR = "concurrent busy,please try again later!";

    /**
     * 并发锁命名空间，用于做功能锁隔离
     * @return
     */
    String namespace() default "";

    /**
     * 加锁失败后置处理器
     * @return
     */
    Class<? extends LockFallback> fallback()[] default {};

    /**
     * 加锁失败后返回字符串
     * 优先级 lockFailedHandler > lockFailedWithException > lockFailedStr
     * @return
     */
    String fallbackStr() default DEFAULT_LOCK_FAILED_STR;

    /**
     * 加锁失败后抛异常，默认不抛异常
     * 优先级  lockFailedHandler > lockFailedWithException > lockFailedStr
     * @return
     */
    Class<? extends Exception> fallbackWithException()[] default {};


    /**
     * 锁释放时间，单位：秒
     * -1 不限制 （基于redisson看门狗每10秒自动续期，服务停止后30秒自动释放锁）
     * @return
     */
    int timeout() default -1;
    /**
     * 等待时间，单位：秒
     * @return
     */
    int waitTime() default 0;

    /**
     * 锁键，用于加锁识别
     * @return
     */
    Key key();


}
