package springbootkit.desensitization.support.logback.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.appender.LogstashTcpSocketAppender;

/**
 * @Description
 * @deprecated use DesensitizedLogFilter instead
 * @Date 2024/11/15
 * @Version 0.0.1
 */
@Deprecated
@Slf4j
public class DesensitizedLogstashTcpSocketFileAppender extends LogstashTcpSocketAppender implements IDesensitizedAppender{

    @Override
    protected void prepareForDeferredProcessing(ILoggingEvent event) {
        try {
            iterateArgs((LoggingEvent) event);
        } catch (Exception e) {
            log.debug("日志脱敏失败：{}",e.getMessage(),e);
        } finally {
            super.prepareForDeferredProcessing(event);
        }
    }

}
