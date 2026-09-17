package io.github.jevonsnotes.jevokit.api.service.impl;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.TimedCache;
import io.github.jevonsnotes.jevokit.api.service.IPreventReplayAttack;
import io.github.jevonsnotes.jevokit.core.lang.LockKey;
import io.github.jevonsnotes.jevokit.core.util.LockUtil;

/**
 * 防重放校验，默认10分钟内不能重复
 */

public class DefaultPreventReplayAttack implements IPreventReplayAttack {
    private final long PERIOD = 10 * 60 * 1000;
    private final TimedCache<String, Object> NONCE_CACHE = CacheUtil.newTimedCache(PERIOD);

    @Override
    public boolean checkReplay(String nonce, String timestamp) {
        long diff = System.currentTimeMillis() - Long.parseLong(timestamp) * 1000;
        if (diff > PERIOD) {
            return true;
        } else {
            Object o = NONCE_CACHE.get(nonce);
            if (o == null) {
                LockKey key = LockUtil.getKey(nonce);
                synchronized (key) {
                    if ((o = NONCE_CACHE.get(nonce)) == null) {
                        NONCE_CACHE.put(nonce, timestamp, diff <= 0 ? PERIOD : PERIOD - diff);
                    }
                }
            }
            return o != null;
        }
    }
}
