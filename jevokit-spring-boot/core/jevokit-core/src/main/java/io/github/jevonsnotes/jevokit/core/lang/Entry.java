package io.github.jevonsnotes.jevokit.core.lang;

import java.util.Map;

/**
 * 可独立使用的键值对：实现 {@link Map.Entry} 以便与 Map API 互操作，但自身不挂靠任何 backing map——
 * {@link #setValue(Object)} 仅修改本对象字段，不会写穿到某个 Map，各方法也不会抛出 JDK 注释中描述的
 * IllegalStateException/UnsupportedOperationException（上述方法注释为接口契约原文，保留作参照）。
 * <p>典型用途：配合 {@code CollectionUtil.extractToMap} 作为提取函数的返回载体。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public class Entry<K,V> implements Map.Entry<K,V> {
    private K key;
    private V val;
    /**
     * 构造空键值对（key/val 均为 null）
     */
    public Entry() {
    }
    /**
     * 以键值构造
     *
     * @param key 键
     * @param val 值
     */
    public Entry(K key, V val) {
        this.key = key;
        this.val = val;
    }

    /**
     * Returns the key corresponding to this entry.
     *
     * @return the key corresponding to this entry
     * @throws IllegalStateException implementations may, but are not
     *                               required to, throw this exception if the entry has been
     *                               removed from the backing map.
     */
    @Override
    public K getKey() {
        return this.key;
    }

    /**
     * Returns the value corresponding to this entry.  If the mapping
     * has been removed from the backing map (by the iterator's
     * <tt>remove</tt> operation), the results of this call are undefined.
     *
     * @return the value corresponding to this entry
     * @throws IllegalStateException implementations may, but are not
     *                               required to, throw this exception if the entry has been
     *                               removed from the backing map.
     */
    @Override
    public V getValue() {
        return this.val;
    }

    /**
     * Replaces the value corresponding to this entry with the specified
     * value (optional operation).  (Writes through to the map.)  The
     * behavior of this call is undefined if the mapping has already been
     * removed from the map (by the iterator's <tt>remove</tt> operation).
     *
     * @param value new value to be stored in this entry
     * @return old value corresponding to the entry
     * @throws UnsupportedOperationException if the <tt>put</tt> operation
     *                                       is not supported by the backing map
     * @throws ClassCastException            if the class of the specified value
     *                                       prevents it from being stored in the backing map
     * @throws NullPointerException          if the backing map does not permit
     *                                       null values, and the specified value is null
     * @throws IllegalArgumentException      if some property of this value
     *                                       prevents it from being stored in the backing map
     * @throws IllegalStateException         implementations may, but are not
     *                                       required to, throw this exception if the entry has been
     *                                       removed from the backing map.
     */
    @Override
    public V setValue(V value) {
        V old = this.val;
        this.val = value;
        return old;
    }
}
