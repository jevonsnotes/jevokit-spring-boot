package io.github.jevonsnotes.jevokit.core.lang;

/**
 * 无参有返回值回调：JDK Supplier 语义的自定义等价物，供 {@code LockUtil.sync} 等工具在加锁/事务上下文中执行并取回结果。
 *
 * @param <R> 回调返回值类型
 */
public interface CallbackR<R> {
    /**
     * 执行回调逻辑
     *
     * @return 回调结果
     */
    R call();
}
