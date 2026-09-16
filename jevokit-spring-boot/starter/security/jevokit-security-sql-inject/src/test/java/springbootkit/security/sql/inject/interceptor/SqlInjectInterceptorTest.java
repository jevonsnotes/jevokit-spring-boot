package springbootkit.security.sql.inject.interceptor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import springbootkit.configuration.annotation.SIClean;
import springbootkit.configuration.holder.ConfigHolder;
import springbootkit.configuration.security.sql.inject.SqlInjectProperties;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SqlInjectInterceptor：preHandle 按 @SIClean 注解与全局开关装配 ThreadLocal 元数据。
 */
class SqlInjectInterceptorTest {

    static class Controller {
        public void noAnnotation() {
        }

        @SIClean(mode = SqlInjectProperties.Mode.reject)
        public void methodMode() {
        }

        @SIClean(disabled = true)
        public void disabled() {
        }
    }

    @SIClean(mode = SqlInjectProperties.Mode.clear)
    static class AnnotatedController {
        public void method() {
        }

        @SIClean(disabled = true)
        public void methodDisabled() {
        }
    }

    private final SqlInjectProperties properties = new SqlInjectProperties();
    private final SqlInjectInterceptor interceptor = new SqlInjectInterceptor(properties);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void tearDown() {
        ConfigHolder.removeSI();
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
        assertThat(ConfigHolder.getSIMeta().isEnabled()).as("非控制层不得装配元数据").isFalse();
    }

    @Test
    @DisplayName("全局开关关闭时放行且不装配元数据")
    void disabledPropertiesSkips() throws Exception {
        properties.setEnabled(false);
        assertThat(interceptor.preHandle(request, response, handler(Controller.class, "noAnnotation"))).isTrue();
        assertThat(ConfigHolder.getSIMeta().isEnabled()).as("开关关闭不得装配元数据").isFalse();
    }

    @Test
    @DisplayName("无注解方法：按全局配置装配")
    void noAnnotationUsesGlobalConfig() throws Exception {
        properties.setEnabled(true);
        properties.setMode(SqlInjectProperties.Mode.reject);
        assertThat(interceptor.preHandle(request, response, handler(Controller.class, "noAnnotation"))).isTrue();
        assertThat(ConfigHolder.getSIMeta().isEnabled()).isTrue();
        assertThat(ConfigHolder.getSIMeta().getMode())
                .as("无注解时 mode 取全局值")
                .isEqualTo(SqlInjectProperties.Mode.reject);
    }

    @Test
    @DisplayName("方法级 @SIClean(mode) 覆盖全局 mode")
    void methodAnnotationOverridesMode() throws Exception {
        properties.setEnabled(true);
        properties.setMode(SqlInjectProperties.Mode.clear);
        assertThat(interceptor.preHandle(request, response, handler(Controller.class, "methodMode"))).isTrue();
        assertThat(ConfigHolder.getSIMeta().getMode())
                .as("@SIClean(mode=reject) 必须覆盖全局 clear")
                .isEqualTo(SqlInjectProperties.Mode.reject);
    }

    @Test
    @DisplayName("类级 @SIClean 对无注解方法生效（回退语义）")
    void classLevelAnnotationApplies() throws Exception {
        properties.setEnabled(true);
        properties.setMode(SqlInjectProperties.Mode.reject);
        assertThat(interceptor.preHandle(request, response, handler(AnnotatedController.class, "method"))).isTrue();
        assertThat(ConfigHolder.getSIMeta().getMode())
                .as("方法无注解时回退到类注解")
                .isEqualTo(SqlInjectProperties.Mode.clear);
    }

    @Test
    @DisplayName("方法级 @SIClean 覆盖类级（Spring 惯例，BUG-8 修复锁死）")
    void methodLevelAnnotationOverridesClassLevel() throws Exception {
        properties.setEnabled(true);
        properties.setMode(SqlInjectProperties.Mode.reject);
        // AnnotatedController.methodDisabled 方法上有 @SIClean(disabled=true)，类上有 @SIClean(mode=clear)
        assertThat(interceptor.preHandle(request, response, handler(AnnotatedController.class, "methodDisabled"))).isTrue();
        assertThat(ConfigHolder.getSIMeta().isEnabled())
                .as("方法级 disabled=true 必须覆盖类级注解（不再被类注解遮蔽）")
                .isFalse();
    }

    @Test
    @DisplayName("方法级 @SIClean(disabled=true) 且无类注解时跳过装配")
    void disabledAnnotationSkips() throws Exception {
        properties.setEnabled(true);
        assertThat(interceptor.preHandle(request, response, handler(Controller.class, "disabled"))).isTrue();
        assertThat(ConfigHolder.getSIMeta().isEnabled()).as("显式关闭注解不得装配元数据").isFalse();
    }

    @Test
    @DisplayName("afterCompletion 清理 ThreadLocal")
    void afterCompletionRemovesThreadLocal() throws Exception {
        properties.setEnabled(true);
        interceptor.preHandle(request, response, handler(Controller.class, "noAnnotation"));
        interceptor.afterCompletion(request, response, null, null);
        assertThat(ConfigHolder.getSIMeta().isEnabled()).as("afterCompletion 后必须重置").isFalse();
    }

    @Test
    @DisplayName("afterConcurrentHandlingStarted 清理 ThreadLocal")
    void afterConcurrentHandlingStartedRemovesThreadLocal() throws Exception {
        properties.setEnabled(true);
        interceptor.preHandle(request, response, handler(Controller.class, "noAnnotation"));
        interceptor.afterConcurrentHandlingStarted(request, response, null);
        assertThat(ConfigHolder.getSIMeta().isEnabled()).isFalse();
    }
}
