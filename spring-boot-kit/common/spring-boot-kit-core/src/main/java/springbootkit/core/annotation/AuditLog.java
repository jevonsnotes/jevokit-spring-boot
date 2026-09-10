package springbootkit.core.annotation;

import java.lang.annotation.*;

/**
 * @Description 启用审计日志
 * @Date 2022/6/29 10:50
 * @Version 0.0.1
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {
    /**
     * 事件类型
     * @return
     */
    String eventType();
    /**
     * 事件名称
     * @return
     */
    String eventName();
}
