package springbootkit.core.util;

import cn.hutool.core.lang.func.Func1;
import cn.hutool.core.lang.func.VoidFunc1;
import springbootkit.core.lang.Entry;


import java.util.*;

public class CollectionUtil extends cn.hutool.core.collection.CollectionUtil {
    public static <T,R>List<R> extract(Iterable<T> collection, Func1<T,R> func) {
        return extract(collection, func, false,false);
    }

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

    public static <K,V> V  getAndSetIfNull(Map<K,V> map,K key,V defaultV){
        V v = map.computeIfAbsent(key, k -> defaultV);
        return v;
    }
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

    public static <E>List<E> toList(Set<E> set){
        List<E> res = new ArrayList<>();
        if(CollectionUtil.isNotEmpty(set)){
            res = (List<E>) addAll(res,set);
        }
        return res;
    }
}
