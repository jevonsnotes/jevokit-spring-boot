package io.github.jevonsnotes.jevokit.redis.token.strategy;

import cn.hutool.extra.spring.SpringUtil;
import io.github.jevonsnotes.jevokit.redis.token.delegate.TokenDelegate;

/**
 *
 */
public interface TokenStrategy {

    /**
     * 惰性获取 token 代理（原为接口 static 字段在类初始化期 getBean，形成类加载顺序依赖：
     * SpringUtil 未就绪时任何实现类加载即抛异常——终验裁定改为调用期查找）。
     * 每次调用直查，无共享可变状态；SpringUtil.getBean 自身即容器单例查找，成本可接受。
     */
    static TokenDelegate delegate() {
        return SpringUtil.getBean(TokenDelegate.class);
    }

    /**
     * 代理获取token，线程安全
     * @return
     */
    default String getTokenDelegate(){
        return delegate().getToken(this);
    };

    /**
     * 代理刷新token，线程安全
     * @return
     */
    default String refreshTokenDelegate(){
        return delegate().refreshToken(this);
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
