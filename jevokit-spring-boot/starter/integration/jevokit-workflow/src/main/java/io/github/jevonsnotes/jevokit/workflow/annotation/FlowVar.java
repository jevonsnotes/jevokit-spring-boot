package io.github.jevonsnotes.jevokit.workflow.annotation;

import io.github.jevonsnotes.jevokit.workflow.consts.FlowScope;

import java.lang.annotation.*;

/**
 * @Description 流程变量注解
 * @Date 2023/5/11 17:59
 * @Version 0.0.1
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FlowVar {
    /**
     * 变量域
     * @return FlowScope
     */
    FlowScope scope() default FlowScope.GLOBAL;

    /**
     * 是否忽略空值
     * @return boolean
     */
    boolean ignoreIfNull() default true;
}
