package io.github.jevonsnotes.jevokit.desensitization.annotation;

import cn.hutool.core.util.StrUtil;

import java.lang.annotation.*;

/**
 * 开启脱敏返回
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Desensitization {
}
