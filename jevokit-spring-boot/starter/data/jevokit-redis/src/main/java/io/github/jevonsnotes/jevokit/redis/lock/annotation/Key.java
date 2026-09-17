package io.github.jevonsnotes.jevokit.redis.lock.annotation;

import java.lang.annotation.*;

 /**
 * 分布式锁键定位注解（配合 @Lock 使用）：value 直接声明键（优先级最高）；否则按
 * paramAt 选参数下标，再沿 route 属性路径（a.b.c）取值拼键；print 控制是否把键值
 * 拼进降级文案 fallbackStr 便于排查撞锁。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Key {
    /**
     * 锁键 用于直接声明，优先级高于route
     */
    String value() default "";
    /**
     * 方法参数下标，从0开始（默认第一个参数）
     */
    int[] paramAt() default 0;
    /**
     * 锁键路由,用于定位目标参数属性(默认当前对象转字符串)
     * 格式：property1.property2.property3
     */
    String[] route() default {};

    /**
     * 是否自动将key拼接到fallbackStr
     */
    boolean print() default false;
}
