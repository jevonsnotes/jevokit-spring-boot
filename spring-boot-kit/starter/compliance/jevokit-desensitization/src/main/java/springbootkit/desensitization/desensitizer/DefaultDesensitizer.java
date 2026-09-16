package springbootkit.desensitization.desensitizer;

import springbootkit.desensitization.annotation.DstProperty;
import springbootkit.desensitization.dto.DstContext;
import springbootkit.desensitization.util.DesUtil;

import java.lang.reflect.Field;

/**
 * @Description TODO
 * @Date 2022/8/2 17:25
 * @Version 0.0.1
 */
public class DefaultDesensitizer implements Desensitizer{


    @Override
    public String des(Object object, Field currentField, String currentVal, DstContext context) {
        return DesUtil.des(currentVal,context);
    }

}
