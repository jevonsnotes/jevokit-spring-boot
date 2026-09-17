package io.github.jevonsnotes.jevokit.core.lang;

/**
 * 单参有返回值回调：JDK Function 语义的自定义等价物，典型如字符串过滤器（入参原串、返回处理后新串）。
 *
 * @param <P> 回调入参类型
 * @param <R> 回调返回值类型
 */
public interface CallbackPR<P,R> {
    /**
     * 执行回调逻辑
     *
     * @param p 回调入参
     * @return 回调结果
     */
    R call(P p);
}
