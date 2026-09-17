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

    /**
     * 进程内防重放：时间戳超 10 分钟窗口判为过期；窗口内 nonce 首见则登记放行，已见判为重放。
     *
     * @param nonce     请求随机码（同 nonce 二次到达即重放）
     * @param timestamp 秒级时间戳（与当前时间差超过窗口即失效）
     * @return true 表示重放或已过期应拒绝；false 首次有效请求
     * @throws NumberFormatException timestamp 非数字时抛出（协议报文非法）
     */
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
