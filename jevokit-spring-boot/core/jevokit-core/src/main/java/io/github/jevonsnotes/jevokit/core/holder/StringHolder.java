package io.github.jevonsnotes.jevokit.core.holder;

/**
 * String 可变值信封：在 lambda/匿名类回调中承载可变的字符串状态（绕开局部变量 effectively final 约束）。
 */
public class StringHolder implements Holder{
    public String value;
    public StringHolder(String value) {
        this.value = value;
    }
}
