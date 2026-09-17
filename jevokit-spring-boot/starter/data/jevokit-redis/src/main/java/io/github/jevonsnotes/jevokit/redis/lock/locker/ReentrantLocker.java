package io.github.jevonsnotes.jevokit.redis.lock.locker;


import io.github.jevonsnotes.jevokit.redis.util.RedisLock;

public abstract class ReentrantLocker<T> {
    /**
     * 默认非阻塞锁，可自定义复写
     *
     * @param key
     * @return
     */
    public boolean lock(String key) {
        return RedisLock.tryLock(key);
    }

    /**
     * 解锁
     *
     * @param key
     */
    private void unlock(String key) {
        RedisLock.unlock(key);
    }

    /**
     * 包装主体
     */
    public abstract T wrap();

    /**
     * 上锁失败
     */
    public T lockFailed() {
        return null;
    }

    ;

    /**
     * 执行
     *
     * @param key
     */
    public T exec(String key) {

        if (lock(key)) {
            try {
                return wrap();
            } finally {
                unlock(key);
            }
        } else {
            return lockFailed();
        }

    }

}
