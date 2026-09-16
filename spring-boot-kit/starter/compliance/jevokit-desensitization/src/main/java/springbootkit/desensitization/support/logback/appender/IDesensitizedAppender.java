package springbootkit.desensitization.support.logback.appender;

import ch.qos.logback.classic.spi.LoggingEvent;
import cn.hutool.core.util.ReflectUtil;
import springbootkit.desensitization.consts.DstConst;
import springbootkit.desensitization.helper.DesHelper;
import springbootkit.desensitization.support.logback.IDesensitizedLogback;

/**
 * use DesensitizedLogFilter instead
 */
@Deprecated
public interface IDesensitizedAppender extends IDesensitizedLogback {
}
