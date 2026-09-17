package io.github.jevonsnotes.jevokit.desensitization.annotation;

import io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstType;
import io.github.jevonsnotes.jevokit.desensitization.desensitizer.DefaultDesensitizer;
import io.github.jevonsnotes.jevokit.desensitization.desensitizer.Desensitizer;

import java.lang.annotation.*;

/**
 * 字段级脱敏标注：声明被标注字段/方法返回值/参数按指定类型与权限级别脱敏。
 * 使用方式：实体字段加 {@code @DstProperty(type = DstType.MOBILE, level = DstLevel.LV3)}，
 * 由 {@code DesensitizationAspect}（@Desensitization 触发）在返回值序列化前依注解逐字段脱敏。
 * <p>关键约束：type=CUSTOM 时须给出自定义 {@link Desensitizer} 实现（无参可构造，反射实例化并缓存）；
 * ignore=true 直接跳过；level 是"查看该明文所需的最低权限档"，与 SensitizationPowerInterceptor 配合
 * 实现有权限角色看明文（见 DstLevel）。
 */
@Target({ElementType.FIELD,ElementType.METHOD,ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DstProperty {
    /**
     * 脱敏类型
     * @return
     */
    DstType type() default DstType.DEFAULT;

    /**
     * 权限键值，可搭配 SensitizationPowerInterceptor 细化用户允许明文查看某个敏感信息
     * @return default is LV9
     */
    DstLevel level() default DstLevel.LV9;

    /**
     * 当脱敏类型为自定义 {@link DstType#CUSTOM} 时必录
     * @return
     */
    Class<? extends Desensitizer> desensitizer() default DefaultDesensitizer.class;

    boolean ignore() default false;
}
