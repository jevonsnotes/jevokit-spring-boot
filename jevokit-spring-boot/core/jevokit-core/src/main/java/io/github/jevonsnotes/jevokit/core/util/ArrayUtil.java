package io.github.jevonsnotes.jevokit.core.util;

import cn.hutool.core.lang.Editor;

/**
 * @Description TODO
 * @Date 2025/3/4
 * @Version 0.0.1
 */
public class ArrayUtil {
    public static <T>T[] edit(Class<T> clazz, Object array,Editor editor) {
        T[] cast = (T[]) array;
        for(T t : cast) {
            editor.edit(t);
        }
        return cast;
    }

}
