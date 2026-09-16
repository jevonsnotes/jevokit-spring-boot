package springbootkit.redis.util;

import cn.hutool.core.lang.func.Func0;
import cn.hutool.core.lang.func.VoidFunc0;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

public class RedisLock {
    private static RedissonClient client;

    public RedisLock(RedissonClient redissonClient){
        client = redissonClient;
    }

    public static void lock(String key, long leaseTime, TimeUnit unit){
        client.getLock(key).lock(leaseTime,unit);
    }
    public static void lock(String key){
        client.getLock(key).lock();
    }

    public static boolean tryLock(String key){
        return tryLock(key,60);
    }

    public static boolean tryLock(String key, long leaseTime){
        return tryLock(key,0,leaseTime, TimeUnit.SECONDS);
    }

    public static boolean tryLock(String key,long waitTime, long leaseTime, TimeUnit unit){
        try {
            return client.getLock(key).tryLock(waitTime,leaseTime, unit);
        } catch (InterruptedException e) {
            return false;
        }
    }
    public static void unlock(String key){
        client.getLock(key).unlock();
    }

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
