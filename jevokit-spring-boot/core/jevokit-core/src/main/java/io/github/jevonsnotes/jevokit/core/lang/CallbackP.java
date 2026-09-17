package io.github.jevonsnotes.jevokit.core.lang;

/**
 * 单参无返回值回调：JDK Consumer 语义的自定义等价物，供工具类逐元素回调（如 {@code XmlUtil.editNode} 的节点编辑器）。
 *
 * @param <P> 回调入参类型
 */
public interface CallbackP<P> {
    /**
     * 执行回调逻辑
     *
     * @param p 回调入参
     */
    void call(P p);
}
