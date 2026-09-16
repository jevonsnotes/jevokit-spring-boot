package springbootkit.core.util;

import java.lang.reflect.Type;

/**
 * @Description TODO
 * @Date 2025/6/27
 * @Version 0.0.1
 */
public class ClassUtil extends cn.hutool.core.util.ClassUtil {

    public static boolean isClassAssignable(Type targetType, Type sourceType) {
        if (targetType instanceof Class && sourceType instanceof Class) {

           return isAssignable((Class<?>) targetType,(Class<?>) sourceType);
        }
        return false;
    }
}
