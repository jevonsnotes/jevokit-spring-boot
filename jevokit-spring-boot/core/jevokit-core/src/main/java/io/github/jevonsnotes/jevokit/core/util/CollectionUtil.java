package io.github.jevonsnotes.jevokit.core.util;

import cn.hutool.core.lang.func.Func1;
import cn.hutool.core.lang.func.VoidFunc1;
import io.github.jevonsnotes.jevokit.core.lang.Entry;


import java.util.*;

/**
 * 集合工具，在 hutool CollectionUtil 基础上补充"按函数提取/转换"与"遍历回调"类能力。
 * <p>常用入口：{@link #extract(Iterable, Func1)} 属性提取、{@link #extractToMap(Iterable, Func1, boolean)} 转 Map、
 * {@link #getAndSetIfNull(Map, Object, Object)} 缺省值补位、{@link #iterate(Map, VoidFunc1)}/{@link #iterate(Collection, VoidFunc1)} 安全遍历。
 * <p>关键语义：回调抛出的受检异常统一包装为 RuntimeException 上抛；extract 的 ignoreNull 只过滤 null 结果，
 * extractToMap 的 ignoreNull 额外过滤 key 或 value 为 null 的 Entry（未开启时 entry 为 null 也会被跳过，防止 NPE）。
 */
public class CollectionUtil extends cn.hutool.core.collection.CollectionUtil {
    /**
     * 按函数提取集合元素的字段值（不去重、保留 null）
     *
     * @param collection 来源集合
     * @param func       提取函数
     * @param <T>        元素类型
     * @param <R>        提取结果类型
     * @return 提取结果列表，与来源顺序一致
     */
    public static <T,R>List<R> extract(Iterable<T> collection, Func1<T,R> func) {
        return extract(collection, func, false,false);
    }

    /**
     * 按函数提取集合元素的字段值
     *
     * @param collection 来源集合
     * @param func       提取函数
     * @param ignoreNull true 时跳过提取结果为 null 的元素
     * @param unique     true 时结果去重（依 equals 判重）
     * @param <T>        元素类型
     * @param <R>        提取结果类型
     * @return 提取结果列表
     */
    public static <T,R>List<R> extract(Iterable<T> collection, Func1<T,R> func, boolean ignoreNull,boolean unique) {
        List<R> fieldValueList = new ArrayList<>();
        try {
            Iterator<T> i$ = collection.iterator();
            while(true) {
                R value;
                do {
                    if (!i$.hasNext()) {
                        return fieldValueList;
                    }

                    T bean = i$.next();
                    value = func.call(bean);
                } while(null == value && ignoreNull);

                //是否去重唯一
                if(!unique || !fieldValueList.contains(value)){
                    fieldValueList.add(value);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 按函数把集合转成 Map（后出现的同 key 覆盖先出现的）
     *
     * @param collection 来源集合
     * @param func       提取函数，返回 {@link Entry} 作为键值对
     * @param ignoreNull true 时跳过 key 或 value 为 null 的 Entry
     * @param <T>        元素类型
     * @param <K>        结果键类型
     * @param <V>        结果值类型
     * @return 转换结果 Map
     */
    public static <T,K,V> Map<K,V> extractToMap(Iterable<T> collection, Func1<T, Entry<K,V>> func, boolean ignoreNull) {
        Map<K,V> fieldValueList = new HashMap<>();
        try {
            Iterator<T> i$ = collection.iterator();

            while(true) {
                Map.Entry<K,V> entry;
                do {
                    if (!i$.hasNext()) {
                        return fieldValueList;
                    }
                    T bean = i$.next();
                    entry = func.call(bean);
                } while((null == entry || entry.getKey() == null || entry.getValue() == null) && ignoreNull);
                //entry为空时自动忽略
                if(null != entry){
                    fieldValueList.put(entry.getKey(),entry.getValue());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 取值，缺失时写入并返回固定默认值
     *
     * @param map     目标 Map
     * @param key     键
     * @param defaultV 缺省值，仅在首次缺失时写入并返回同一引用（后续调用返回首次写入的值）
     * @param <K>     键类型
     * @param <V>     值类型
     * @return 已有值或默认值
     */
    public static <K,V> V  getAndSetIfNull(Map<K,V> map,K key,V defaultV){
        V v = map.computeIfAbsent(key, k -> defaultV);
        return v;
    }
    /**
     * 取值，缺失时反射实例化该类型写入并返回
     *
     * @param map    目标 Map
     * @param key    键
     * @param vClass 值类型，需有无参构造
     * @param <K>    键类型
     * @param <V>    值类型
     * @return 已有值或新建实例
     */
    public static <K,V> V  getAndSetIfNull(Map<K,V> map,K key,Class<V> vClass){
        try {
            V v = map.get(key);
            if(v==null){
                v = vClass.newInstance();
                map.put(key,v);
            }
            return v;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * 遍历 Map（空安全，回调受检异常包装为 RuntimeException 上抛）
     *
     * @param map  目标 Map
     * @param func 逐项回调
     * @param <K>  键类型
     * @param <V>  值类型
     */
    public static <K,V>void iterate(Map<K,V> map, VoidFunc1<Map.Entry<K, V>> func){
        try {
            if(CollectionUtil.isNotEmpty(map)){
                Set<Map.Entry<K, V>> entries = map.entrySet();
                for (Map.Entry<K, V> next : entries) {
                    func.call(next);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * 遍历集合（空安全，回调受检异常包装为 RuntimeException 上抛）
     *
     * @param list 目标集合
     * @param func 逐项回调
     * @param <T>  元素类型
     */
    public static <T>void iterate(Collection<T> list, VoidFunc1<T> func){
        try {
            if(CollectionUtil.isNotEmpty(list)){
                for (T t : list) {
                    func.call(t);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Set 转 List（保持迭代顺序）
     *
     * @param set 来源集合
     * @param <E> 元素类型
     * @return 新建列表；入参为空集合时返回空列表
     */
    public static <E>List<E> toList(Set<E> set){
        List<E> res = new ArrayList<>();
        if(CollectionUtil.isNotEmpty(set)){
            res = (List<E>) addAll(res,set);
        }
        return res;
    }
}
