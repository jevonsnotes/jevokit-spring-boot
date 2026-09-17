package io.github.jevonsnotes.jevokit.core.holder;

/**
 * int 可变值信封：在 lambda/匿名类回调中承载可变的 int 计数或游标（绕开局部变量 effectively final 约束），
 * 如 {@code PageUtil}/{@code JsonUtil} 中的页号/下标游标。
 */
public class IntHolder implements Holder{
    /** 承载的 int 值（公开字段，回调内直接读写） */
    public int value;
    /**
     * 以初始值构造信封
     *
     * @param value 初始值
     */
    public IntHolder(int value) {
        this.value = value;
    }
}
