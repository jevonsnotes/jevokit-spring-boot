package springbootkit.configuration.annotation;

import springbootkit.configuration.security.xss.XssProperties;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface XssClean {
    /**
     * 是否关闭Xss识别
     * @return
     */
    boolean disabled() default false;

    /**
     * 模式
     * 模式数组内仅第一个元素有效，留空默认取全局配置
     * @return
     */
    XssProperties.Mode[] mode() default {};

    boolean ignoreResponse() default true;

}
