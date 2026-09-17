package io.github.jevonsnotes.jevokit.redis.cache.consts;

 /**
 * redis组件常量
 */
public class RConst {
    /**
     * 失效时间识别符
     */
    public static final String REDIS_EXPIRE_TIME_KEY = "#expire";

    /**
     * @Cacheable注解unless常用固定表达式
     */
    public static class Unless {
        public static final String REMOTE_IF_NULL = "#result == null";
    }
}
