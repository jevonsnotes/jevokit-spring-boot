package io.github.jevonsnotes.jevokit.desensitization.support.logback.filter;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import lombok.extern.slf4j.Slf4j;
import io.github.jevonsnotes.jevokit.desensitization.support.logback.IDesensitizedLogback;

/**
 * @Description 日志脱敏过滤器
 * 在appender下添加 <filter class="io.github.jevonsnotes.jevokit.desensitization.support.logback.filter.DesensitizedLogFilter"/>
 * @Date 2026/4/7
 * @Version 0.0.1
 */
@Slf4j
public class DesensitizedLogFilter extends Filter<ILoggingEvent> implements IDesensitizedLogback {
    @Override
    public FilterReply decide(ILoggingEvent event) {
        try {
            this.iterateArgs(event);
        } catch (Exception e) {
            log.debug("日志脱敏失败：{}", e.getMessage(), e);
        }
        return FilterReply.NEUTRAL;
    }
}
