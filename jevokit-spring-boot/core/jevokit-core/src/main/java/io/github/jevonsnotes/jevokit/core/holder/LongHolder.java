package io.github.jevonsnotes.jevokit.core.holder;

/**
 * long 可变值信封：在 lambda/匿名类回调中承载可变的 long 状态（绕开局部变量 effectively final 约束），
 * 常用于累计计数与游标传递。
 */
public class LongHolder implements Holder{
    public long value;
    public LongHolder(long value) {
        this.value = value;
    }
}
