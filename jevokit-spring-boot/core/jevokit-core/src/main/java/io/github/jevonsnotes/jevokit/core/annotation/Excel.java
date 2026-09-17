package io.github.jevonsnotes.jevokit.core.annotation;

import java.lang.annotation.*;


@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@Documented
public @interface Excel {
    /**
     * 请求参数名
     * 默认为空，自动读取方法参数名作为请求参数名 或 有且只有一个文件时，自动绑定
     * @return
     */
    String param() default "";

    /**
     * 忽略前几行,从1开始计数(默认0全读)
     */
    int ignoreRows() default 0;

    /**
     * 处理指定下标的sheet页
     * @return
     */
    int sheet() default 0;
}
