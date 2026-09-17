package io.github.jevonsnotes.jevokit.core.annotation;

import java.lang.annotation.*;


/**
 * Excel 上传参数绑定注解：标注在 Controller 方法的 List 参数上，由 jevokit-excel 的 ExcelResolver
 * 把上传内容解析为行对象列表注入该参数。支持两种提交格式——multipart 文件上传（SAX 流式读取）
 * 与 JSON 数组请求体；Excel 列与行对象字段按字段声明顺序对应（顺序可用 @BeanField 调整）。
 * <p>各属性提供解析控制：param 指定请求中的文件参数名（缺省自动探测）、ignoreRows 跳过表头行、sheet 选择工作表下标。
 */
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
