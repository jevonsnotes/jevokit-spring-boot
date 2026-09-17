package io.github.jevonsnotes.jevokit.core.holder;

/**
 * @Description GenericHolder
 * @Date 2025/6/26
 * @Version 0.0.1
 */
public class GenericHolder<T> implements Holder{
    public T value;
    public GenericHolder(T value) {
        this.value = value;
    }
    public GenericHolder() {
    }
}
