package io.github.jevonsnotes.jevokit.redis.util;

import cn.hutool.core.lang.func.Func0;
import cn.hutool.core.lang.func.VoidFunc0;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

public class RedisLock {
    private static RedissonClient client;

    /**
    * 构造即把容器中的 RedissonClient 写入 static 字段（静态门面由 Spring 装配驱动——
    * 单元测试自行 new 多实例会互相覆盖，属已知取舍）。
    * @param redissonClient 容器装配的 Redisson 客户端
    */
    public RedisLock(RedissonClient redissonClient){
        client = redissonClient;
    }

    /**
    * 阻塞获取锁（不等待，拿不到就挂起），到 leaseTime 自动释放。
    * @param key 锁键
    * @param leaseTime 持有时长
    * @param unit 时长单位
    */
    public static void lock(String key, long leaseTime, TimeUnit unit){
        client.getLock(key).lock(leaseTime,unit);
    }
    /**
    * 阻塞获取锁，无 leaseTime（Redisson watchdog 续期，进程存活即持有）。
    * @param key 锁键
    */
    public static void lock(String key){
        client.getLock(key).lock();
    }

    /**
    * 尝试获取（不等待），默认 60 秒自动释放。
    * @param key 锁键
    * @return true=获得锁；false=被占用或线程被中断（中断按失败处理，不抛出）
    */
    public static boolean tryLock(String key){
        return tryLock(key,60);
    }

    /**
    * 尝试获取（不等待），指定释放秒数。
    * @param key 锁键
    * @param leaseTime 持有秒数
    * @return true=获得锁；false=被占用或中断
    */
    public static boolean tryLock(String key, long leaseTime){
        return tryLock(key,0,leaseTime, TimeUnit.SECONDS);
    }

    /**
    * 尝试获取全参形态：最多等待 waitTime。
    * @param key 锁键
    * @param waitTime 最长等待时长
    * @param leaseTime 持有时长
    * @param unit 单位（等待与持有共用）
    * @return true=获得锁；false=超时/被占用；InterruptedException 被吞为 false（调用方无法区分中断与超时）
    */
    public static boolean tryLock(String key,long waitTime, long leaseTime, TimeUnit unit){
        try {
            return client.getLock(key).tryLock(waitTime,leaseTime, unit);
        } catch (InterruptedException e) {
            return false;
        }
    }
    /**
    * 释放锁（未持有时调用由 Redisson 抛 IllegalMonitorStateException）。
    * @param key 锁键
    */
    public static void unlock(String key){
        client.getLock(key).unlock();
    }

    /**
    * 锁内执行模板：拿锁→执行→finally 释放。
    * @param key 锁键
    * @param waitTime 最长等待秒
    * @param leaseTime 持有秒
    * @param func 业务回调（异常按运行时抛出）
    * @return 成功执行的业务返回值；<b>未拿到锁时返回 null</b>（与业务返回 null 不可区分，慎用于可空返回）
    */
    public static <R> R tryLockWrap(String key, long waitTime, long leaseTime, Func0<R> func){

        if(tryLock(key,waitTime,leaseTime,TimeUnit.SECONDS)){
            try {
                return func.callWithRuntimeException();
            } finally {
               unlock(key);
            }
        }
        return null;
    }
    /**
    * 锁内执行模板（无返回值）：未拿到锁时<b>静默跳过</b>业务。
    * @param key 锁键
    * @param waitTime 最长等待秒
    * @param leaseTime 持有秒
    * @param func 业务回调
    */
    public static void tryLockWrap(String key, long waitTime, long leaseTime, VoidFunc0 func){
        if(tryLock(key,waitTime,leaseTime,TimeUnit.SECONDS)){
            try {
                func.callWithRuntimeException();
            } finally {
               unlock(key);
            }
        }
    }

}
