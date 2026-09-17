package io.github.jevonsnotes.jevokit.sample.desensitizer;

import io.github.jevonsnotes.jevokit.desensitization.consts.DstType;
import io.github.jevonsnotes.jevokit.desensitization.desensitizer.Desensitizer;
import io.github.jevonsnotes.jevokit.desensitization.dto.DstContext;
import io.github.jevonsnotes.jevokit.desensitization.util.DesUtil;
import io.github.jevonsnotes.jevokit.sample.vo.TestVo;

import java.lang.reflect.Field;

/**
 * @Description TODO
 * @Date 2022/8/2 17:58
 * @Version 0.0.1
 */
public class CardDesensitizer implements Desensitizer<TestVo> {
    @Override
    public String des(TestVo object, Field currentField, String currentVal, DstContext context) {
        if(object != null){
            if("1".equals(object.getIdType())) {
                context.setType(DstType.ID_CARD);
                return DesUtil.des(currentVal, context);
            }
        } else {
            context.setType( DstType.DEFAULT);
            return DesUtil.des(currentVal, context);
        }
        return currentVal;
    }
}
