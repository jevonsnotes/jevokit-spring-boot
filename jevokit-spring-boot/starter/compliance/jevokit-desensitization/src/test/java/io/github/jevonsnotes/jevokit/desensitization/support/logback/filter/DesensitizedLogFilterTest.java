package io.github.jevonsnotes.jevokit.desensitization.support.logback.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.jevonsnotes.jevokit.desensitization.DesensitizationTestSupport;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DesensitizedLogFilter 集成测试：
 * 以 logback 真实装配链路（Logger -> Filter -> ListAppender）验证
 * "标准 appender + filter" 组合可替代已废弃的 DesensitizedConsoleAppender /
 * DesensitizedRollingFileAppender——过滤后在 appender 侧观测到的格式化消息，
 * 其敏感字段（手机号）已脱敏。
 * sample 的 logback-spring.xml 已切换为该配置形态，本测试是其防线。
 */
class DesensitizedLogFilterTest {

    private static final String MOBILE = "15818222222";

    /** 采集过滤后事件的 appender（观测点） */
    private ListAppender<ILoggingEvent> observer;
    private Logger logger;
    /** 测试期保持存活的脱敏上下文（遗留 #2 起 ContextClosedEvent 即清理静态引用——
     *  与生产一致：应用运行期间容器存活、脱敏就绪；close 移至 tearDown） */
    private AnnotationConfigApplicationContext desensitizationContext;

    @BeforeEach
    void setUpLogback() {
        // 装配 DesUtil 静态依赖（SM4 密钥 + 规则），与生产同通路。
        // 旧形态"启动上下文→runner 关闭→继续消费静态残留"在关闭即清理契约下不再成立——
        // 改持有存活上下文（同 minimalRunner 的 bean 套件），close 在 tearDown
        desensitizationContext = DesensitizationTestSupport.liveMinimalContext(DesensitizationTestSupport.properties(
                Collections.singletonMap(DstLevel.LV0, Collections.singletonMap(
                        DstType.MOBILE, Collections.singletonList(".*mobile"))),
                Collections.emptyList(),
                Collections.singletonMap(DstLevel.LV0, Collections.singletonList("DST_POWER_LV0"))));

        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        logger = ctx.getLogger(DesensitizedLogFilterTest.class);

        observer = new ListAppender<>();
        observer.start();
        // 标准 appender + 脱敏 filter：被测装配形态
        DesensitizedLogFilter filter = new DesensitizedLogFilter();
        filter.start();
        observer.addFilter(filter);
        logger.addAppender(observer);
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
    }

    @AfterEach
    void tearDownLogback() {
        logger.detachAppender(observer);
        observer.stop();
        if (desensitizationContext != null) {
            // ContextClosedEvent → DesHelper 静态引用清理（用例间零残留）
            desensitizationContext.close();
            desensitizationContext = null;
        }
    }

    @Test
    void jsonStringArgIsDesensitized() {
        String json = "{\"mobile\":\"" + MOBILE + "\"}";

        logger.info("json {}", json);

        assertThat(observer.list).hasSize(1);
        String formatted = observer.list.get(0).getFormattedMessage();
        // JSON 字符串按报文脱敏规则处理（DesUtil.desensitizeJson 走 BASIC 掩码）
        assertThat(formatted).doesNotContain(MOBILE);
    }

    @Test
    void xmlStringArgIsDesensitized() {
        String xml = "<user><mobile>" + MOBILE + "</mobile></user>";

        logger.info("xml {}", xml);

        assertThat(observer.list).hasSize(1);
        String formatted = observer.list.get(0).getFormattedMessage();
        // XML 字符串按报文脱敏规则处理（DesUtil.desensitizeXml 走 BASIC 掩码）
        assertThat(formatted).doesNotContain(MOBILE);
    }

    @Test
    void plainStringArgWithoutSensitivePatternPassesThrough() {
        logger.info("plain {}", "nothing-sensitive-here");

        assertThat(observer.list).hasSize(1);
        String formatted = observer.list.get(0).getFormattedMessage();
        assertThat(formatted).contains("nothing-sensitive-here");
    }

    @Test
    void mapWithMobileKeyIsDesensitized() {
        // Map 入参：key 命中 prop-pattern 的字符串值按 desByProperties 脱敏
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("mobile", MOBILE);
        data.put("remark", "safe-value");

        logger.info("map {}", data);

        assertThat(observer.list).hasSize(1);
        String formatted = observer.list.get(0).getFormattedMessage();
        // "mobile" key 命中的值脱敏；"remark" 未命中原样保留
        assertThat(formatted).doesNotContain(MOBILE);
        assertThat(formatted).contains("safe-value");
    }

    @Test
    void listWithMobileEntryIsDesensitized() {
        java.util.Map<String, Object> entry = new java.util.HashMap<>();
        entry.put("mobile", MOBILE);

        java.util.List<Object> list = new java.util.ArrayList<>();
        list.add(entry);

        logger.info("list {}", list);

        assertThat(observer.list).hasSize(1);
        String formatted = observer.list.get(0).getFormattedMessage();
        assertThat(formatted).doesNotContain(MOBILE);
    }
}
