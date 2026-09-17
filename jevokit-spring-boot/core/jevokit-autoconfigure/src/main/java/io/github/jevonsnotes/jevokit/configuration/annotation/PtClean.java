package io.github.jevonsnotes.jevokit.configuration.annotation;

import io.github.jevonsnotes.jevokit.configuration.security.pathtraversal.PathTraversalProperties;

import java.lang.annotation.*;

/**
 * @Description TODO
 * @Date 2022/5/24 14:29
 * @Version 0.0.1
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PtClean {
        /**
         * 是否关闭识别
         * @return
         */
        boolean disabled() default false;

        /**
         * 模式
         * 模式数组内仅第一个元素有效，留空默认取全局配置
         * @return
         */
        PathTraversalProperties.Mode[] mode() default {};

    }
