package io.github.jevonsnotes.jevokit.desensitization.support.logback.filter;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import lombok.extern.slf4j.Slf4j;
import io.github.jevonsnotes.jevokit.desensitization.support.logback.IDesensitizedLogback;

/**
 * 日志脱敏过滤器
 * 在appender下添加 <filter class="io.github.jevonsnotes.jevokit.desensitization.support.logback.filter.DesensitizedLogFilter"/>
 */
@Slf4j
public class DesensitizedLogFilter extends Filter<ILoggingEvent> implements IDesensitizedLogback {
    /**
     * 日志输出前脱敏参数数组（异常仅 debug 记录——脱敏失败绝不阻断日志输出）。
     *
     * @param event 日志事件
     * @return 恒 NEUTRAL（本过滤器只改写参数，不做过滤决策）
     */
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
