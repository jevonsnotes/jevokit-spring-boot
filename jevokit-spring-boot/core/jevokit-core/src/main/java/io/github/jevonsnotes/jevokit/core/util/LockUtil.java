package io.github.jevonsnotes.jevokit.core.util;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;
import lombok.extern.slf4j.Slf4j;
import io.github.jevonsnotes.jevokit.core.lang.Callback;
import io.github.jevonsnotes.jevokit.core.lang.CallbackR;
import io.github.jevonsnotes.jevokit.core.lang.LockKey;
import io.github.jevonsnotes.jevokit.core.lang.WatchDog;

import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description TODO
 * @Date 2024/12/13
 * @Version 0.0.1
 */
@Slf4j
public class LockUtil {
    /**
     * 创建一个WATCH_DOG，定期清理离上次获取锁超过12小时的锁对象，避免内存溢出
     */
    private static final WatchDog WATCH_DOG;
    /**
     * 锁对象池
     */
    private static final ConcurrentHashMap<String ,LockKey> LOCK_CACHE = new ConcurrentHashMap<>();
    static {
        //创建一个WATCH_DOG
        WATCH_DOG = new WatchDog("LockUtil-Watcher") {
            @Override
            public void watch() {

                Iterator<Map.Entry<String, LockKey>> iterator = LOCK_CACHE.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, LockKey> entry = iterator.next();
                    if(entry.getValue() != null && entry.getValue().getUpdateTime().before(DateUtil.offset(DateUtil.date(), DateField.HOUR_OF_DAY,-12))){
                        iterator.remove();
                        log.debug("lock key {} has expired and has bean removed. current pool size {}", entry.getKey(),LOCK_CACHE.size());
                    }
                }
            }
        };
        // 延迟1秒执行，间隔60秒重复执行
        WATCH_DOG.start(1000,60000);
    }
    /**
     * 获取一个简单锁对象
     * @param key 锁值字符串
     * @return LockKey 返回一个转换后的锁对象
     */
    public static LockKey getKey(final String key) {
        Date now = new Date();
        LockKey lockKey = LOCK_CACHE.computeIfAbsent(key, k -> new LockKey(now, now));
        lockKey.setUpdateTime(now);
        return lockKey;
    }

    public static <T>T sync(final String key, CallbackR<T> callback) {
        LockKey lockObject = getKey(key);
        synchronized (lockObject) {
            return callback.call();
        }
    }

    public static void sync(final String key, Callback callback) {
        LockKey lockObject = getKey(key);
        synchronized (lockObject) {
            callback.call();
        }
    }

}
