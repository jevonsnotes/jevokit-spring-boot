package io.github.jevonsnotes.jevokit.core.lang;

/**
 * 双参无返回值回调：JDK BiConsumer 语义的自定义等价物，用于需要成对上下文（如 key + value）的遍历回调。
 *
 * @param <P1> 第一个入参类型
 * @param <P2> 第二个入参类型
 */
public interface CallbackP2<P1,P2> {
    /**
     * 执行回调逻辑
     *
     * @param p1 第一个入参
     * @param p2 第二个入参
     */
    void call(P1 p1,P2 p2);
}
