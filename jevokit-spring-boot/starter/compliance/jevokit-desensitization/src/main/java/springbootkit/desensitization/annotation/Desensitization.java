package springbootkit.desensitization.annotation;

import cn.hutool.core.util.StrUtil;

import java.lang.annotation.*;

/**
 * @Description 开启脱敏返回
 * @Date 2022/7/11 15:45
 * @Version 0.0.1
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Desensitization {
}
