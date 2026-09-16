package springbootkit.desensitization.support.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import cn.hutool.core.util.ReflectUtil;
import springbootkit.desensitization.consts.DstConst;
import springbootkit.desensitization.helper.DesHelper;

/**
 * @Description TODO
 * @Date 2026/4/7
 * @Version 0.0.1
 */
public interface IDesensitizedLogback {
    default void iterateArgs(ILoggingEvent event) {
        Object[] argumentArray = event.getArgumentArray();
        if(argumentArray != null) {
            for(int i = 0; i < argumentArray.length; i++) {
                Object o = argumentArray[i];
                if(o != null && !ignore(o.getClass())) {
                    argumentArray[i] = desensitize(o);
                }
            }
            ReflectUtil.setFieldValue(event,"formattedMessage",null);
            event.getFormattedMessage();
        }
    }

    default Object desensitize(Object obj) {
        return DesHelper.desensitizeLog(obj);
    }

    default boolean ignore(Class<?> clazz) {
        return DstConst.DESENSITIZATION_IGNORE_CLASS.contains(clazz.getName());
    }

}
