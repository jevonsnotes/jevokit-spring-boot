package io.github.jevonsnotes.jevokit.desensitization.support.logback.appender;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import lombok.extern.slf4j.Slf4j;

/**
 * @deprecated use DesensitizedLogFilter instead
 */
@Deprecated
@Slf4j
public class DesensitizedConsoleAppender<E> extends ConsoleAppender<E> implements IDesensitizedAppender{
    @Override
    /**
     * 输出前脱敏事件参数再写控制台（@Deprecated：改用 DesensitizedLogFilter，理由见接口注释）。
     */
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
