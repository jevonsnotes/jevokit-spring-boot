package io.github.jevonsnotes.jevokit.core.util;

import cn.hutool.core.lang.Editor;

/**
 * 数组工具，在 hutool ArrayUtil 之外补充"逐元素原地编辑"能力。
 * <p>常用入口：{@link #edit(Class, Object, Editor)}。
 * <p>关键约束：Editor 的返回值被忽略，元素引用本身不会被替换，因此编辑器必须原地修改传入对象才生效
 * （与 hutool Editor 惯例的"返回新值"不同）。
 */
public class ArrayUtil {
    /**
     * 逐元素编辑数组（原地修改）
     *
     * @param clazz  元素类型（仅用于泛型约束，未参与运行时逻辑）
     * @param array  待编辑数组，元素须为 clazz 及其子类
     * @param editor 编辑器，返回值被忽略，必须原地修改入参元素
     * @param <T>    元素类型
     * @return 原数组引用（元素已被编辑）
     */
    public static <T>T[] edit(Class<T> clazz, Object array,Editor editor) {
        T[] cast = (T[]) array;
        for(T t : cast) {
            editor.edit(t);
        }
        return cast;
    }

}
