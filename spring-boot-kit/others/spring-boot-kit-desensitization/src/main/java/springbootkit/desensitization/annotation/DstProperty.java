package springbootkit.desensitization.annotation;

import springbootkit.desensitization.consts.DstLevel;
import springbootkit.desensitization.consts.DstType;
import springbootkit.desensitization.desensitizer.DefaultDesensitizer;
import springbootkit.desensitization.desensitizer.Desensitizer;

import java.lang.annotation.*;

/**
 * @Description TODO
 * @Date 2022/7/11 15:19
 * @Version 0.0.1
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
