package io.github.jevonsnotes.jevokit.desensitization.support.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import cn.hutool.core.util.ReflectUtil;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstConst;
import io.github.jevonsnotes.jevokit.desensitization.helper.DesHelper;

/**
 * logback 脱敏 appender 的公共行为接口：appender 输出前遍历日志事件参数并逐个脱敏，
 * 供 {@code DesensitizedConsoleAppender}/{@code DesensitizedRollingFileAppender} 复用。
 * 使用方式：自定义 logback appender 实现本接口，在 doAppend 输出前调 {@link #iterateArgs(ILoggingEvent)}。
 * <p>关键约束：脱敏直接改写事件参数数组并重置 formattedMessage 缓存（置 null 触发按新参数重建），
 * 因此必须发生在消息格式化之后、输出之前；{@link #ignore(Class)} 命中 DstConst 忽略名单的类型
 * （如 xxl-job 反射代理）跳过——这类对象的"脱敏"实为深遍历改写，会破坏其内部状态。
 */
public interface IDesensitizedLogback {
    default void iterateArgs(ILoggingEvent event) {
        Object[] argumentArray = event.getArgumentArray();
        if(argumentArray != null) {
            for(int i = 0; i < argumentArray.length; i++) {
                Object o = argumentArray[i];
                if(o != null && !ignore(o.getClass())) {
                    argumentArray[i] = desensitize(o);
                }
            }
            ReflectUtil.setFieldValue(event,"formattedMessage",null);
            event.getFormattedMessage();
        }
    }

    default Object desensitize(Object obj) {
        return DesHelper.desensitizeLog(obj);
    }

    default boolean ignore(Class<?> clazz) {
        return DstConst.DESENSITIZATION_IGNORE_CLASS.contains(clazz.getName());
    }

}
