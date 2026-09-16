package springbootkit.desensitization.desensitizer;

import springbootkit.desensitization.consts.DstAlgorithm;
import springbootkit.desensitization.dto.DstContext;
import springbootkit.desensitization.util.DesUtil;

import java.lang.reflect.Field;

/**
 * @Description TODO
 * @Date 2022/8/2 17:21
 * @Version 0.0.1
 */
public interface Desensitizer<T> {

    String des(T object, Field currentField, String currentVal, DstContext context);

}
