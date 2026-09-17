package io.github.jevonsnotes.jevokit.desensitization.support.logback.appender;

import ch.qos.logback.classic.spi.LoggingEvent;
import cn.hutool.core.util.ReflectUtil;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstConst;
import io.github.jevonsnotes.jevokit.desensitization.helper.DesHelper;
import io.github.jevonsnotes.jevokit.desensitization.support.logback.IDesensitizedLogback;

/**
 * use DesensitizedLogFilter instead
 */
@Deprecated
public interface IDesensitizedAppender extends IDesensitizedLogback {
}
