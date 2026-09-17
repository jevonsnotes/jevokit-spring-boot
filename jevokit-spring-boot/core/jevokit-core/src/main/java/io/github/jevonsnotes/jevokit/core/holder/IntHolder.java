package io.github.jevonsnotes.jevokit.core.holder;

/**
 * int 可变值信封：在 lambda/匿名类回调中承载可变的 int 计数或游标（绕开局部变量 effectively final 约束），
 * 如 {@code PageUtil}/{@code JsonUtil} 中的页号/下标游标。
 */
public class IntHolder implements Holder{
    public int value;
    public IntHolder(int value) {
        this.value = value;
    }
}
