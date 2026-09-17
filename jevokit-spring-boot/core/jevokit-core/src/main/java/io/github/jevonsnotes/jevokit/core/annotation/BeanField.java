package io.github.jevonsnotes.jevokit.core.annotation;

import java.lang.annotation.*;

/**
 * @Description 标注字段顺序
 * @Date 2022/7/6 9:21
 * @Version 0.0.1
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BeanField {
    /**
     * 顺序优先级，值越小越靠前
     * @return
     */
    int order();
}
