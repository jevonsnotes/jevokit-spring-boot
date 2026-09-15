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
 * BUG-8 对抗性复测（终验 #6）：eng 用例已锁「方法覆盖类（翻转）」与「类 active 回退」，
 * 但类级 fixture 只有 active 形态——<b>类级 disabled=true 的回退方向</b>无人覆盖：
 * <ol>
 *   <li>类级 disabled=true + 方法无注解 → 全类跳过清洗（类级关闭对未标注方法生效）；</li>
 *   <li>类级 disabled=true + 方法级 @XssClean(mode) 显式开启 → 方法级"重新打开"，
 *       不被类级关闭一票闷死（方法覆盖类的正向方向，与 disabled 覆盖互补）。</li>
 * </ol>
 * 另：BUG-8「无旧语义残留」由终验时的全仓注解优先级模式扫描佐证
 * （getDeclaringClass().getAnnotation 仅剩三拦截器且均为方法优先实现）。
 */
@DisplayName("BUG-8 复测：类级 disabled 回退方向 + 方法级重新打开（对抗性）")
class XssClassDisabledFallbackAdversarialTest {

    /** 类级整体关闭 */
    @XssClean(disabled = true)
    static class ClassDisabledController {
        public void noAnnotation() {
        }

        @XssClean(mode = XssProperties.Mode.escape)
        public void methodReopens() {
        }
    }

    private final XssProperties properties;
    private final XssCleanInterceptor interceptor;

    XssClassDisabledFallbackAdversarialTest() {
        properties = new XssProperties();
        properties.setEnabled(true);
        properties.setMode(XssProperties.Mode.clear);
        interceptor = new XssCleanInterceptor(properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        interceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), null, null);
    }

    private HandlerMethod handler(String methodName) throws Exception {
        Method method = ClassDisabledController.class.getMethod(methodName);
        return new HandlerMethod(new ClassDisabledController(), method);
    }

    @Test
    @DisplayName("类级 disabled=true：无注解方法跳过装配（类级关闭对全类生效）")
    void classDisabledSkipsUnannotatedMethods() throws Exception {
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(),
                handler("noAnnotation"))).isTrue();
        assertThat(ConfigHolder.getXssMeta().isEnabled())
                .as("类级 disabled=true 时无注解方法必须跳过清洗装配").isFalse();
    }

    @Test
    @DisplayName("类级 disabled=true：方法级显式开启可重新打开清洗（覆盖正向方向）")
    void methodAnnotationReopensDespiteClassDisabled() throws Exception {
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(),
                handler("methodReopens"))).isTrue();
        assertThat(ConfigHolder.getXssMeta().isEnabled())
                .as("方法级 @XssClean 不应被类级 disabled 一票否决").isTrue();
        assertThat(ConfigHolder.getXssMeta().getMode())
                .as("方法级 mode 应生效").isEqualTo(XssProperties.Mode.escape);
    }
}
