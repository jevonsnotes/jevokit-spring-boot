package io.github.jevonsnotes.jevokit.desensitization.support.logback.appender;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import lombok.extern.slf4j.Slf4j;

/**
 * @deprecated use DesensitizedLogFilter instead
 */
@Deprecated
@Slf4j
public class DesensitizedRollingFileAppender<E> extends RollingFileAppender<E> implements IDesensitizedAppender{

    @Override
    protected void subAppend(E event) {
        try {
            iterateArgs((LoggingEvent) event);
        } catch (Exception e) {
            log.debug("日志脱敏失败：{}",e.getMessage(),e);
        } finally {
            super.subAppend(event);
        }
    }

}
