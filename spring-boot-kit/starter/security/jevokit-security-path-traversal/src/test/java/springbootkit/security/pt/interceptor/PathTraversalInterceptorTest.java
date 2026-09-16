package springbootkit.security.pt.interceptor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import springbootkit.configuration.annotation.PtClean;
import springbootkit.configuration.holder.ConfigHolder;
import springbootkit.configuration.security.pathtraversal.PathTraversalProperties;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PathTraversalInterceptor：preHandle 按 @PtClean 注解与全局开关装配 ThreadLocal 元数据。
 */
class PathTraversalInterceptorTest {

    static class Controller {
        public void noAnnotation() {
        }

        @PtClean(mode = PathTraversalProperties.Mode.clear)
        public void methodMode() {
        }

        @PtClean(disabled = true)
        public void disabled() {
        }
    }

    @PtClean(mode = PathTraversalProperties.Mode.clear)
    static class AnnotatedController {
        public void method() {
        }

        @PtClean(disabled = true)
        public void methodDisabled() {
        }
    }

    private final PathTraversalProperties properties = new PathTraversalProperties();
    private final PathTraversalInterceptor interceptor = new PathTraversalInterceptor(properties);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void tearDown() {
        ConfigHolder.removePt();
    }

    private HandlerMethod handler(Class<?> clazz, String methodName) throws NoSuchMethodException {
        Method method = clazz.getMethod(methodName);
        return new HandlerMethod(new Object(), method);
    }

    @Test
    @DisplayName("非 HandlerMethod 处理器直接放行且不装配元数据")
    void nonHandlerMethodSkips() throws Exception {
        properties.setEnabled(true);
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(ConfigHolder.getPtMeta().isEnabled()).as("非控制层不得装配元数据").isFalse();
    }

    @Test
    @DisplayName("全局开关关闭时放行且不装配元数据")
    void disabledPropertiesSkips() throws Exception {
        properties.setEnabled(false);
        assertThat(interceptor.preHandle(request, response, handler(Controller.class, "noAnnotation"))).isTrue();
        assertThat(ConfigHolder.getPtMeta().isEnabled()).as("开关关闭不得装配元数据").isFalse();
    }

    @Test
    @DisplayName("无注解方法：按全局配置装配（默认 reject）")
    void noAnnotationUsesGlobalConfig() throws Exception {
        properties.setEnabled(true);
        properties.setMode(PathTraversalProperties.Mode.reject);
        assertThat(interceptor.preHandle(request, response, handler(Controller.class, "noAnnotation"))).isTrue();
        assertThat(ConfigHolder.getPtMeta().isEnabled()).isTrue();
        assertThat(ConfigHolder.getPtMeta().getMode())
                .as("无注解时 mode 取全局值")
                .isEqualTo(PathTraversalProperties.Mode.reject);
    }

    @Test
    @DisplayName("方法级 @PtClean(mode) 覆盖全局 mode")
    void methodAnnotationOverridesMode() throws Exception {
        properties.setEnabled(true);
        properties.setMode(PathTraversalProperties.Mode.reject);
        assertThat(interceptor.preHandle(request, response, handler(Controller.class, "methodMode"))).isTrue();
        assertThat(ConfigHolder.getPtMeta().getMode())
                .as("@PtClean(mode=clear) 必须覆盖全局 reject")
                .isEqualTo(PathTraversalProperties.Mode.clear);
    }

    @Test
    @DisplayName("类级 @PtClean 对无注解方法生效（回退语义）")
    void classLevelAnnotationApplies() throws Exception {
        properties.setEnabled(true);
        properties.setMode(PathTraversalProperties.Mode.reject);
        assertThat(interceptor.preHandle(request, response, handler(AnnotatedController.class, "method"))).isTrue();
        assertThat(ConfigHolder.getPtMeta().getMode())
                .as("方法无注解时回退到类注解")
                .isEqualTo(PathTraversalProperties.Mode.clear);
    }

    @Test
    @DisplayName("方法级 @PtClean 覆盖类级（Spring 惯例，BUG-8 修复锁死）")
    void methodLevelAnnotationOverridesClassLevel() throws Exception {
        properties.setEnabled(true);
        properties.setMode(PathTraversalProperties.Mode.reject);
        // AnnotatedController.methodDisabled 方法上有 @PtClean(disabled=true)，类上有 @PtClean(mode=clear)
        assertThat(interceptor.preHandle(request, response, handler(AnnotatedController.class, "methodDisabled"))).isTrue();
        assertThat(ConfigHolder.getPtMeta().isEnabled())
                .as("方法级 disabled=true 必须覆盖类级注解（不再被类注解遮蔽）")
                .isFalse();
    }

    @Test
    @DisplayName("方法级 @PtClean(disabled=true) 且无类注解时跳过装配")
    void disabledAnnotationSkips() throws Exception {
        properties.setEnabled(true);
        assertThat(interceptor.preHandle(request, response, handler(Controller.class, "disabled"))).isTrue();
        assertThat(ConfigHolder.getPtMeta().isEnabled()).as("显式关闭注解不得装配元数据").isFalse();
    }

    @Test
    @DisplayName("afterCompletion 清理 ThreadLocal")
    void afterCompletionRemovesThreadLocal() throws Exception {
        properties.setEnabled(true);
        interceptor.preHandle(request, response, handler(Controller.class, "noAnnotation"));
        interceptor.afterCompletion(request, response, null, null);
        assertThat(ConfigHolder.getPtMeta().isEnabled()).as("afterCompletion 后必须重置").isFalse();
    }

    @Test
    @DisplayName("afterConcurrentHandlingStarted 清理 ThreadLocal")
    void afterConcurrentHandlingStartedRemovesThreadLocal() throws Exception {
        properties.setEnabled(true);
        interceptor.preHandle(request, response, handler(Controller.class, "noAnnotation"));
        interceptor.afterConcurrentHandlingStarted(request, response, null);
        assertThat(ConfigHolder.getPtMeta().isEnabled()).isFalse();
    }
}
