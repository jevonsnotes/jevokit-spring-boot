package io.github.jevonsnotes.jevokit.core.holder;

/**
 * 泛型可变值信封：在 lambda/匿名类回调中承载可变的任意类型状态（绕开局部变量 effectively final 约束）。
 * <p>与特化的 IntHolder/LongHolder 等的差别仅在于泛型包装带来的装箱开销。
 *
 * @param <T> 承载值类型
 */
public class GenericHolder<T> implements Holder{
    public T value;
    public GenericHolder(T value) {
        this.value = value;
    }
    public GenericHolder() {
    }
}
