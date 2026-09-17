package io.github.jevonsnotes.jevokit.core.holder;

/**
 * String 可变值信封：在 lambda/匿名类回调中承载可变的字符串状态（绕开局部变量 effectively final 约束）。
 */
public class StringHolder implements Holder{
    /** 承载的字符串值（公开字段，回调内直接读写） */
    public String value;
    /**
     * 以初始值构造信封
     *
     * @param value 初始值
     */
    public StringHolder(String value) {
        this.value = value;
    }
}
