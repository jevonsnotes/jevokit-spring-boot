package io.github.jevonsnotes.jevokit.core.lang;

/**
 * 无参无返回值回调：JDK Runnable 语义的自定义等价物，供工具类（如 {@code LockUtil.sync}）以受检异常更宽松的方式传递代码块。
 * <p>与 java.util.function 包的对应关系：Callback ≈ Runnable；同族还有 CallbackR（有返回）、CallbackP（单参）等按 P/R 后缀组合的变体。
 */
public interface Callback {
    /**
     * 执行回调逻辑
     */
    void call();
}
