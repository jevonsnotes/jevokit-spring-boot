package io.github.jevonsnotes.jevokit.core.annotation;

import java.lang.annotation.*;

/**
 * 启用审计日志
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
