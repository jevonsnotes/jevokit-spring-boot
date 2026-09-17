package io.github.jevonsnotes.jevokit.core.annotation;

import java.lang.annotation.*;

/**
 * 标注字段顺序
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
