package springbootkit.redis.token.strategy;

import cn.hutool.extra.spring.SpringUtil;
import springbootkit.redis.token.delegate.TokenDelegate;

/**
 *
 */
public interface TokenStrategy {
    TokenDelegate delegate = SpringUtil.getBean(TokenDelegate.class);

    /**
     * 代理获取token，线程安全
     * @return
     */
    default String getTokenDelegate(){
        return delegate.getToken(this);
    };

    /**
     * 代理刷新token，线程安全
     * @return
     */
    default String refreshTokenDelegate(){
        return delegate.refreshToken(this);
    };

    /**
     * 获取token逻辑实现
     * 获取token时，若redis返回空，则进入此方法
     * @param key
     * @return
     */
    String getToken(String key);

    /**
     * 超时逻辑实现
     * 获取或刷新token等待超时时，调用此方法
     * @param key
     * @return
     */
    default String timeOut(String key){
        return getToken(key);
    };

    /**
     * 刷新token逻辑实现
     * @param key
     * @return
     */
    String refreshToken(String key);

    /**
     * tokenKey生成逻辑实现
     * @return
     */
    String getTokenKey();

}
