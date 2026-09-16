package springbootkit.security.xss.interceptor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import springbootkit.configuration.annotation.XssClean;
import springbootkit.configuration.holder.ConfigHolder;
import springbootkit.configuration.security.xss.XssProperties;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XssCleanInterceptor：preHandle 按 @XssClean 注解与全局开关装配 ThreadLocal 元数据，
 * afterCompletion/afterConcurrentHandlingStarted 负责清理。
 */
class XssCleanInterceptorTest {

    static class Controller {
        public void noAnnotation() {
        }

        @XssClean(mode = XssProperties.Mode.escape)
        public void methodMode() {
        }

        @XssClean(disabled = true)
        public void disabled() {
        }

        @XssClean(mode = XssProperties.Mode.reject, ignoreResponse = false)
        public void methodModeAndResponse() {
        }

        public void classLevelOnly() {
        }
    }

    @XssClean(mode = XssProperties.Mode.reject)
    static class AnnotatedController {
        public void method() {
        }

        @XssClean(disabled = true)
        public void methodDisabled() {
        }
    }

    private final XssProperties properties = new XssProperties();
    private final XssCleanInterceptor interceptor = new XssCleanInterceptor(properties);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void tearDown() {
        ConfigHolder.removeXss();
    }

    private HandlerMethod handler(Class<?> clazz, String methodName) throws NoSuchMethodException {
        Method method = clazz.getMethod(methodName);
        // HandlerMethod 只需要 bean 实例与 method，用占位 bean 避免真实容器
        return new HandlerMethod(new Object(), method);
    }

    private void global(XssProperties.Mode mode, boolean enabled, boolean ignoreResponse) {
        properties.setMode(mode);
        properties.setEnabled(enabled);
        properties.setIgnoreResponse(ignoreResponse);
    }

    @Test
    @DisplayName("非 HandlerMethod 处理器直接放行且不装配元数据")
    void nonHandlerMethodSkips() throws Exception {
        global(XssProperties.Mode.clear, true, true);
        boolean proceed = interceptor.preHandle(request, response, new Object());
        assertThat(proceed).as("非控制层处理器必须放行").isTrue();
        assertThat(ConfigHolder.getXssMeta().isEnabled())
                .as("未命中控制层时不得开启脱敏元数据")
                .isFalse();
    }

    @Test
    @DisplayName("全局开关关闭时放行且不装配元数据")
    void disabledPropertiesSkips() throws Exception {
        global(XssProperties.Mode.clear, false, true);
        boolean proceed = interceptor.preHandle(request, response, handler(Controller.class, "noAnnotation"));
        assertThat(proceed).as("开关关闭必须放行").isTrue();
        assertThat(ConfigHolder.getXssMeta().isEnabled()).as("开关关闭不得装配元数据").isFalse();
    }

    @Test
    @DisplayName("无注解方法：按全局配置装配（mode/ignoreResponse 透传）")
    void noAnnotationUsesGlobalConfig() throws Exception {
        global(XssProperties.Mode.clear, true, false);
        assertThat(interceptor.preHandle(request, response, handler(Controller.class, "noAnnotation"))).isTrue();
        assertThat(ConfigHolder.getXssMeta().isEnabled()).as("命中控制层必须置 enabled").isTrue();
        assertThat(ConfigHolder.getXssMeta().getMode()).as("无注解时 mode 取全局值").isEqualTo(XssProperties.Mode.clear);
        assertThat(ConfigHolder.getXssMeta().isIgnoreResponse()).as("无注解时 ignoreResponse 取全局值").isFalse();
    }

    @Test
    @DisplayName("方法级 @XssClean(mode) 覆盖全局 mode")
    void methodAnnotationOverridesMode() throws Exception {
        global(XssProperties.Mode.clear, true, true);
        assertThat(interceptor.preHandle(request, response, handler(Controller.class, "methodMode"))).isTrue();
        assertThat(ConfigHolder.getXssMeta().getMode())
                .as("@XssClean(mode=escape) 必须覆盖全局 clear")
                .isEqualTo(XssProperties.Mode.escape);
    }

    @Test
    @DisplayName("方法级 @XssClean 同时覆盖 mode 与 ignoreResponse")
    void methodAnnotationOverridesIgnoreResponse() throws Exception {
        global(XssProperties.Mode.clear, true, true);
        assertThat(interceptor.preHandle(request, response, handler(Controller.class, "methodModeAndResponse"))).isTrue();
        assertThat(ConfigHolder.getXssMeta().getMode()).isEqualTo(XssProperties.Mode.reject);
        assertThat(ConfigHolder.getXssMeta().isIgnoreResponse())
                .as("@XssClean(ignoreResponse=false) 必须覆盖全局默认 true")
                .isFalse();
    }

    @Test
    @DisplayName("@XssClean(disabled=true) 跳过装配")
    void disabledAnnotationSkips() throws Exception {
        global(XssProperties.Mode.clear, true, true);
        assertThat(interceptor.preHandle(request, response, handler(Controller.class, "disabled"))).isTrue();
        assertThat(ConfigHolder.getXssMeta().isEnabled()).as("显式关闭注解不得装配元数据").isFalse();
    }

    @Test
    @DisplayName("类级 @XssClean 对无注解方法生效（回退语义）")
    void classLevelAnnotationApplies() throws Exception {
        global(XssProperties.Mode.clear, true, true);
        assertThat(interceptor.preHandle(request, response, handler(AnnotatedController.class, "method"))).isTrue();
        assertThat(ConfigHolder.getXssMeta().getMode())
                .as("方法无注解时回退到类注解")
                .isEqualTo(XssProperties.Mode.reject);
    }

    @Test
    @DisplayName("方法级 @XssClean 覆盖类级（Spring 惯例，BUG-8 修复锁死）")
    void methodLevelAnnotationOverridesClassLevel() throws Exception {
        global(XssProperties.Mode.clear, true, true);
        // AnnotatedController.methodDisabled 方法上有 @XssClean(disabled=true)，类上有 @XssClean(mode=reject)
        assertThat(interceptor.preHandle(request, response, handler(AnnotatedController.class, "methodDisabled"))).isTrue();
        assertThat(ConfigHolder.getXssMeta().isEnabled())
                .as("方法级 disabled=true 必须覆盖类级注解（不再被类注解遮蔽）")
                .isFalse();
    }

    @Test
    @DisplayName("afterCompletion 清理 ThreadLocal，避免线程复用串味")
    void afterCompletionRemovesThreadLocal() throws Exception {
        global(XssProperties.Mode.clear, true, true);
        interceptor.preHandle(request, response, handler(Controller.class, "noAnnotation"));
        assertThat(ConfigHolder.getXssMeta().isEnabled()).isTrue();
        interceptor.afterCompletion(request, response, null, null);
        assertThat(ConfigHolder.getXssMeta().isEnabled())
                .as("afterCompletion 后 ThreadLocal 必须重置")
                .isFalse();
    }

    @Test
    @DisplayName("afterConcurrentHandlingStarted 同样清理 ThreadLocal")
    void afterConcurrentHandlingStartedRemovesThreadLocal() throws Exception {
        global(XssProperties.Mode.clear, true, true);
        interceptor.preHandle(request, response, handler(Controller.class, "noAnnotation"));
        interceptor.afterConcurrentHandlingStarted(request, response, null);
        assertThat(ConfigHolder.getXssMeta().isEnabled()).isFalse();
    }
}
