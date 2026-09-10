package springbootkit.test.server.desensitizer;

import springbootkit.desensitization.consts.DstType;
import springbootkit.desensitization.desensitizer.Desensitizer;
import springbootkit.desensitization.dto.DstContext;
import springbootkit.desensitization.util.DesUtil;
import springbootkit.test.server.vo.TestVo;

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
