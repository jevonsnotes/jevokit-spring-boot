package io.github.jevonsnotes.jevokit.configuration.annotation;

import io.github.jevonsnotes.jevokit.configuration.security.pathtraversal.PathTraversalProperties;

import java.lang.annotation.*;

/**
 * 路径穿越清洗开关注解：标注在 Controller 类/方法上，对目标范围内的请求启用或关闭路径穿越（../ 等）识别清洗，
 * 并可覆盖全局清洗模式；未标注时走 jevokit.pt.* 全局配置。方法级注解优先于类级。
 * <p>与 SIClean（SQL 注入）、XssClean（XSS）构成安全三件套注解，语义同构。
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
