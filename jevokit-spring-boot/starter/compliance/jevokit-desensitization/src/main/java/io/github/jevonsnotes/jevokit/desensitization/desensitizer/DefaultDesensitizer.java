package io.github.jevonsnotes.jevokit.desensitization.desensitizer;

import io.github.jevonsnotes.jevokit.desensitization.annotation.DstProperty;
import io.github.jevonsnotes.jevokit.desensitization.dto.DstContext;
import io.github.jevonsnotes.jevokit.desensitization.util.DesUtil;

import java.lang.reflect.Field;

/**
 * 默认脱敏器：不做任何自定义处理，直接委托 {@link DesUtil#des(String, DstContext)}
 * 按上下文的类型/级别走通用脱敏规则，是 {@code @DstProperty.desensitizer} 的默认值。
 * 使用方式：无需显式指定；自定义实现可参考本类"委托通用工具"的形态，保持与全局算法/权限语义一致。
 */
public class DefaultDesensitizer implements Desensitizer{


    @Override
    public String des(Object object, Field currentField, String currentVal, DstContext context) {
        return DesUtil.des(currentVal,context);
    }

}
