package io.github.jevonsnotes.jevokit.desensitization.desensitizer;

import io.github.jevonsnotes.jevokit.desensitization.consts.DstAlgorithm;
import io.github.jevonsnotes.jevokit.desensitization.dto.DstContext;
import io.github.jevonsnotes.jevokit.desensitization.util.DesUtil;

import java.lang.reflect.Field;

/**
 * @Description TODO
 * @Date 2022/8/2 17:21
 * @Version 0.0.1
 */
public interface Desensitizer<T> {

    String des(T object, Field currentField, String currentVal, DstContext context);

}
