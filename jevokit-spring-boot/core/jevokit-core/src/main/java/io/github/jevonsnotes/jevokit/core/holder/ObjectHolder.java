package io.github.jevonsnotes.jevokit.core.holder;

/**
 * Object 可变值信封：在 lambda/匿名类回调中承载可变的对象引用（绕开局部变量 effectively final 约束）。
 * 泛型需求请改用 {@link GenericHolder} 以免强转。
 */
public class ObjectHolder implements Holder{
    /** 承载的对象引用（公开字段，回调内直接读写） */
    public Object value;
    /**
     * 以初始值构造信封
     *
     * @param value 初始值
     */
    public ObjectHolder(Object value) {
        this.value = value;
    }
    /**
     * 构造空信封（value 为 null）
     */
    public ObjectHolder() {
    }
}
