package io.github.jevonsnotes.jevokit.core.holder;

/**
 * 泛型可变值信封：在 lambda/匿名类回调中承载可变的任意类型状态（绕开局部变量 effectively final 约束）。
 * <p>与特化的 IntHolder/LongHolder 等的差别仅在于泛型包装带来的装箱开销。
 *
 * @param <T> 承载值类型
 */
public class GenericHolder<T> implements Holder{
    /** 承载的泛型值（公开字段，回调内直接读写） */
    public T value;
    /**
     * 以初始值构造信封
     *
     * @param value 初始值
     */
    public GenericHolder(T value) {
        this.value = value;
    }
    /**
     * 构造空信封（value 为 null）
     */
    public GenericHolder() {
    }
}
