package io.github.jevonsnotes.jevokit.core.holder;

/**
 * long 可变值信封：在 lambda/匿名类回调中承载可变的 long 状态（绕开局部变量 effectively final 约束），
 * 常用于累计计数与游标传递。
 */
public class LongHolder implements Holder{
    /** 承载的 long 值（公开字段，回调内直接读写） */
    public long value;
    /**
     * 以初始值构造信封
     *
     * @param value 初始值
     */
    public LongHolder(long value) {
        this.value = value;
    }
}
