package io.github.jevonsnotes.jevokit.core.util;

import java.lang.reflect.Type;

/**
 * 类工具，在 hutool ClassUtil 基础上补充 Type 级别的可赋值判断。
 * <p>常用入口：{@link #isClassAssignable(Type, Type)}，用于反射/反序列化场景先做粗粒度类型闸门。
 * <p>关键约束：仅当 target 与 source 都是原始 Class（非 ParameterizedType 等泛型 Type）才委派 hutool 判断，
 * 泛型类型一律保守返回 false——调用方需自行处理泛型擦除后的细化判断。
 */
public class ClassUtil extends cn.hutool.core.util.ClassUtil {

    /**
     * 判断 sourceType 是否可赋值给 targetType（仅支持双方均为 Class 的场景）
     *
     * @param targetType 目标类型
     * @param sourceType 来源类型
     * @return 任一方为非 Class 的 Type（如 ParameterizedType）时返回 false，否则等价于 hutool isAssignable
     */
    public static boolean isClassAssignable(Type targetType, Type sourceType) {
        if (targetType instanceof Class && sourceType instanceof Class) {

           return isAssignable((Class<?>) targetType,(Class<?>) sourceType);
        }
        return false;
    }
}
