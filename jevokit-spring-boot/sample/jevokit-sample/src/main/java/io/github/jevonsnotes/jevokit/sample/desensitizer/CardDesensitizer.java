package io.github.jevonsnotes.jevokit.sample.desensitizer;

import io.github.jevonsnotes.jevokit.desensitization.consts.DstType;
import io.github.jevonsnotes.jevokit.desensitization.desensitizer.Desensitizer;
import io.github.jevonsnotes.jevokit.desensitization.dto.DstContext;
import io.github.jevonsnotes.jevokit.desensitization.util.DesUtil;
import io.github.jevonsnotes.jevokit.sample.vo.TestVo;

import java.lang.reflect.Field;

/**
 * 自定义字段脱敏器示例：按对象 idType 动态改写脱敏类型（证件类型 1 走身份证规则，
 * 其余走 DEFAULT）——演示 Desensitizer 扩展点"类型随行"的用法。
 */
public class CardDesensitizer implements Desensitizer<TestVo> {
    @Override
/**
    * 证件类型动态脱敏：idType=1 按身份证规则，否则 DEFAULT；object 为 null 时整体走 DEFAULT。
    * @param object 目标对象（读 idType 分流）
    * @param currentField 当前字段
    * @param currentVal 当前值
    * @param context 脱敏上下文（写回目标类型）
    * @return 脱敏结果
    */
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
