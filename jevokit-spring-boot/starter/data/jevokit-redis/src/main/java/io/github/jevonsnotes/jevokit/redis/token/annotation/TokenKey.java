package io.github.jevonsnotes.jevokit.redis.token.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TokenKey {
    String value();
}