package io.github.jevonsnotes.jevokit.audit.log.filter;

import cn.hutool.extra.spring.SpringUtil;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import io.github.jevonsnotes.jevokit.audit.log.dto.AuditLogDto;
import io.github.jevonsnotes.jevokit.audit.log.dto.AuditLogEventDto;
import io.github.jevonsnotes.jevokit.audit.log.processor.AuditLogProcessor;
import io.github.jevonsnotes.jevokit.audit.log.util.AuditLogUtils;
import io.github.jevonsnotes.jevokit.configuration.audit.log.AuditLogProperties;
import io.github.jevonsnotes.jevokit.configuration.config.IpConfiguration;
import io.github.jevonsnotes.jevokit.core.consts.HttpMethod;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * AuditLogFilter ThreadLocal 清理回归测试（bug-jsecure-context-threadlocal-leak 同类隐患闭环）。
 *
 * <p>被验证的缺陷：{@code AuditLogFilter#doFilter} 的 {@code chain.doFilter} 未包 try/finally，
 * 下游（业务/其他 filter）抛异常时 {@code afterHandle} 不执行——其内部 finally 的 {@code clear()}
 * 是 EVENTS 事件链与 SRC_SERVLET_PATH 两个 ThreadLocal 的唯一清理点，缺失即残留，
 * 线程池复用会把上个请求的事件链/源路径串到下一请求（globalKey 继承 = 两次独立调用被串成同一次链路）。
 *
 * <p>验证通路：直接以 MockHttpServletRequest/MockHttpServletResponse 调用被测 filter（真实包装/匹配逻辑），
 * 经 ApplicationContextRunner 装配最小上下文（与 desensitization 测试基座同模式；getEventType 的
 * SpringUtil.getBean 已改为注入字段直读——遗留 #1 复核——上下文仅作环境兜底）。
 * SRC_SERVLET_PATH 在测试链内手动 setSrcServletPath 写入——模拟
 * forward 归因的生产写入方（该 API 供业务 forward 场景调用）。
 *
 * <p>断言方向：全部断言"<b>正确行为</b>"（无泄漏时应当成立）——修复前本类前三个用例红、修复后全绿。
 */
@DisplayName("AuditLogFilter ThreadLocal 泄漏回归：下游异常时事件链/源路径必须清理")
class AuditLogFilterThreadLocalLeakTest {

    private static final String EVENT_TYPE = "QUERY";
    private static final String AUDITED_PATH_1 = "/api/orders/1";
    private static final String AUDITED_PATH_2 = "/api/orders/2";

    private final IpConfiguration ipConfig = ipConfig();

    @BeforeEach
    void cleanThreadLocals() {
        // 每个用例从干净状态出发，保证用例间互不污染（生产代码缺的清理，测试侧补齐以保证可重复）
        AuditLogUtils.removeSrcServletPath();
        eventsThreadLocal().remove();
    }

    // ------------------------------------------------------------------
    // 用例 1（核心回归）：下游抛受检异常 → 两个 ThreadLocal 清理为空
    // ------------------------------------------------------------------

    @Test
    @DisplayName("下游抛 ServletException：异常透传且 EVENTS/SRC_SERVLET_PATH 均清理为空")
    void checkedExceptionFromDownstreamStillClearsThreadLocals() {
        AuditLogProperties properties = enabledProperties(false);
        AuditLogProcessor processor = new RecordingProcessor();
        minimalRunner(properties).run(context -> {
            AuditLogFilter filter = new AuditLogFilter(processor, properties, ipConfig);

            assertThatThrownBy(() -> filter.doFilter(
                    auditedRequest(AUDITED_PATH_1), new MockHttpServletResponse(), boomChain("downstream checked boom")))
                    .isInstanceOf(ServletException.class)
                    .as("异常应原样向上透传（审计清理不得吞异常）")
                    .hasMessage("downstream checked boom");

            // doFilter 已在本线程同步返回（含异常），请求生命周期已结束，线程上下文应为空
            assertSoftly(softly -> {
                softly.assertThat(AuditLogUtils.getSrcServletPath())
                        .as("SRC_SERVLET_PATH 应被 afterHandle 的 clear() 清理，实际残留=%s",
                                AuditLogUtils.getSrcServletPath())
                        .isNull();
                softly.assertThat(eventsThreadLocal().get())
                        .as("EVENTS 事件链应被 afterHandle 的 clear() 清理，实际残留=%s",
                                eventsThreadLocal().get())
                        .isNull();
            });
        });
    }

    // ------------------------------------------------------------------
    // 用例 2：下游抛非受检异常 → 同样清理为空（容器常见 RuntimeException 通路）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("下游抛 RuntimeException：异常透传且 EVENTS/SRC_SERVLET_PATH 均清理为空")
    void uncheckedExceptionFromDownstreamStillClearsThreadLocals() {
        AuditLogProperties properties = enabledProperties(false);
        AuditLogProcessor processor = new RecordingProcessor();
        minimalRunner(properties).run(context -> {
            AuditLogFilter filter = new AuditLogFilter(processor, properties, ipConfig);

            FilterChain boom = (req, res) -> {
                AuditLogUtils.setSrcServletPath("/forward/source");
                throw new IllegalStateException("downstream unchecked boom");
            };

            assertThatThrownBy(() -> filter.doFilter(
                    auditedRequest(AUDITED_PATH_1), new MockHttpServletResponse(), boom))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("downstream unchecked boom");

            assertSoftly(softly -> {
                softly.assertThat(AuditLogUtils.getSrcServletPath())
                        .as("RuntimeException 路径同样必须清理 SRC_SERVLET_PATH，实际残留=%s",
                                AuditLogUtils.getSrcServletPath())
                        .isNull();
                softly.assertThat(eventsThreadLocal().get())
                        .as("RuntimeException 路径同样必须清理 EVENTS，实际残留=%s",
                                eventsThreadLocal().get())
                        .isNull();
            });
        });
    }

    // ------------------------------------------------------------------
    // 用例 3（串请求行为学证据）：同线程复用后，残留事件不得串到下一请求
    // ------------------------------------------------------------------

    @Test
    @DisplayName("同线程复用：异常请求的事件不得被下一请求继承（globalKey 不得相同）")
    void staleEventMustNotChainIntoNextRequestOnSameThread() {
        // around 开启：请求 1 在 preHandle 即落一条"开始审计"（携带本次的 globalKey），用于取残留证据
        AuditLogProperties properties = enabledProperties(true);
        RecordingProcessor processor = new RecordingProcessor();
        minimalRunner(properties).run(context -> {
            AuditLogFilter filter = new AuditLogFilter(processor, properties, ipConfig);

            // 请求 1：下游抛异常（修复前 EVENTS 残留本线程）
            assertThatThrownBy(() -> filter.doFilter(
                    auditedRequest(AUDITED_PATH_1), new MockHttpServletResponse(),
                    (req, res) -> { throw new ServletException("boom-1"); }))
                    .hasMessage("boom-1");

            List<AuditLogDto> firstRequestSaved = processor.saved.stream()
                    .filter(dto -> AUDITED_PATH_1.equals(dto.getServletPath())).collect(Collectors.toList());
            assertThat(firstRequestSaved)
                    .as("前置：around 开启时请求1 在 preHandle 已落开始审计（携带 globalKey 供继承比对）")
                    .isNotEmpty();
            String firstGlobalKey = firstRequestSaved.get(0).getGlobalKey();

            // 请求 2：同线程正常请求（模拟容器线程池同线程复用，doFilter 同步执行天然等价）
            filter.doFilter(auditedRequest(AUDITED_PATH_2), new MockHttpServletResponse(), (req, res) -> { });

            List<AuditLogDto> secondRequestSaved = processor.saved.stream()
                    .filter(dto -> AUDITED_PATH_2.equals(dto.getServletPath())).collect(Collectors.toList());
            assertThat(secondRequestSaved)
                    .as("请求2 应产生自己的审计记录（around 开启时 pre/after 各一条）")
                    .isNotEmpty();
            assertThat(secondRequestSaved)
                    .as("请求2 的审计不得继承请求1 的 globalKey（相同即实锤：残留事件把两次独立调用串成同一次链路）")
                    .allSatisfy(dto -> assertThat(dto.getGlobalKey()).isNotEqualTo(firstGlobalKey));
        });
    }

    // ------------------------------------------------------------------
    // 用例 4（正常路径回归）：修复不改变正常行为——恰好落一次审计且清理
    // ------------------------------------------------------------------

    @Test
    @DisplayName("正常路径行为不变：around 关闭仍恰好落一次审计且 ThreadLocal 清理（不重复清理）")
    void normalPathStillSavesOnceAndClearsThreadLocals() {
        AuditLogProperties properties = enabledProperties(false);
        RecordingProcessor processor = new RecordingProcessor();
        minimalRunner(properties).run(context -> {
            AuditLogFilter filter = new AuditLogFilter(processor, properties, ipConfig);

            filter.doFilter(auditedRequest(AUDITED_PATH_1), new MockHttpServletResponse(), (req, res) -> { });

            assertThat(processor.saved)
                    .as("around 关闭时应恰好落一次完成审计（修复不得引入重复落审计）")
                    .hasSize(1);
            assertThat(processor.saved.get(0).getEventType()).isEqualTo(EVENT_TYPE);
            assertThat(processor.saved.get(0).getServletPath()).isEqualTo(AUDITED_PATH_1);
            assertThat(AuditLogUtils.getSrcServletPath()).as("正常路径 SRC_SERVLET_PATH 应清理").isNull();
            assertThat(eventsThreadLocal().get()).as("正常路径 EVENTS 应清理").isNull();
        });
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 开启审计且 GET /api/orders/** 命中 QUERY 事件的配置（编程式，等价 yml 绑定结果）。 */
    private static AuditLogProperties enabledProperties(boolean enabledAround) {
        AuditLogProperties properties = new AuditLogProperties();
        properties.setEnabled(true);
        properties.setEnabledAround(enabledAround);
        properties.getEventTypes().setPatterns(
                Collections.singletonMap(HttpMethod.GET,
                        Collections.singletonMap(EVENT_TYPE, Collections.singletonList("/api/orders/**"))));
        return properties;
    }

    /** 历史脚手架：曾为 getEventType 内 SpringUtil.getBean(AuditLogProperties) 提供运行期上下文；
     *  遗留 #1 复核后 getEventType 已改为装配期注入字段直读（不再查询容器），本上下文保留
     *  仅作测试环境兜底（与生产装配形态一致，不构成断言依赖）。
     *  RefreshAutoConfiguration 注册 RefreshScope——AuditLogProperties 带 @RefreshScope，
     *  无 RefreshScope 时 getBean 报 "No Scope registered for scope name 'refresh'"。 */
    private static ApplicationContextRunner minimalRunner(AuditLogProperties properties) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RefreshAutoConfiguration.class))
                .withBean(SpringUtil.class)
                .withBean(AuditLogProperties.class, () -> properties);
    }

    /** 命中审计规则的 GET 请求（显式设 servletPath：路径匹配与 DTO 归因均以 getServletPath 为准）。 */
    private static MockHttpServletRequest auditedRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }

    /** 下游必抛 ServletException 的过滤链：先模拟 forward 归因写入源路径（SRC_SERVLET_PATH 的生产写入方）。 */
    private static FilterChain boomChain(String message) {
        return (req, res) -> {
            AuditLogUtils.setSrcServletPath("/forward/source");
            throw new ServletException(message);
        };
    }

    /** 取 AuditLogUtils 私有静态 EVENTS ThreadLocal（无公开读接口，回归断言用反射只读）。 */
    @SuppressWarnings("unchecked")
    private static ThreadLocal<AuditLogEventDto> eventsThreadLocal() {
        try {
            Field field = AuditLogUtils.class.getDeclaredField("EVENTS");
            field.setAccessible(true);
            return (ThreadLocal<AuditLogEventDto>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法读取 AuditLogUtils.EVENTS（回归测试基础设施失败）", e);
        }
    }

    /** 固定本机地址的 IpConfiguration（save 组装审计 DTO 需要，不触发真实网口探测）。 */
    private static IpConfiguration ipConfig() {
        IpConfiguration ipConfig = new IpConfiguration();
        ipConfig.setIp("10.0.0.1");
        ipConfig.setPort(8080);
        return ipConfig;
    }

    /** 捕获型处理器：记录 save 的 DTO（代替 DB/日志落盘，断言落审计次数与归因内容）。 */
    private static final class RecordingProcessor implements AuditLogProcessor {
        private final List<AuditLogDto> saved = new CopyOnWriteArrayList<>();

        @Override
        public String getUserCode() {
            return "leak-tester";
        }

        @Override
        public String getComCode() {
            return "LEAK_COM";
        }

        @Override
        public void save(AuditLogDto dto) {
            saved.add(dto);
        }
    }
}
