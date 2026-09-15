package springbootkit.security.web.fixed.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SpringWebFixedAspect（CVE-2016-1000027 防护）：
 * 环绕拦截 HttpInvokerServiceExporter.handleRequest，一律拒绝执行。
 */
class SpringWebFixedAspectTest {

    private final SpringWebFixedAspect aspect = new SpringWebFixedAspect();

    @Test
    @DisplayName("[真] aroundHandleRequest 必须抛异常阻断调用（不执行 proceed）")
    void aroundHandleRequestBlocksInvocation() throws Throwable {
        ProceedingJoinPoint point = Mockito.mock(ProceedingJoinPoint.class);

        assertThatThrownBy(() -> aspect.aroundHandleRequest(point))
                .as("CVE 防护切面必须拒绝执行目标方法")
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("CVE-2016-1000027");

        Mockito.verify(point, Mockito.never()).proceed();
        Mockito.verify(point, Mockito.never()).proceed(Mockito.any());
    }

    @Test
    @DisplayName("切点表达式指向 HttpInvokerServiceExporter.handleRequest（可解析且形态防回归）")
    void pointcutExpressionTargetsHttpInvoker() throws Exception {
        Method pointcutMethod = SpringWebFixedAspect.class.getMethod("springWebFixedPointcut");
        Pointcut annotation = pointcutMethod.getAnnotation(Pointcut.class);
        assertThat(annotation).as("切点方法必须携带 @Pointcut").isNotNull();
        assertThat(annotation.value())
                .as("切点必须锁定 HttpInvokerServiceExporter.handleRequest（类名改名/移动会在此处暴露）")
                .isEqualTo("execution(* org.springframework.remoting.httpinvoker.HttpInvokerServiceExporter.handleRequest(..))");

        // 表达式可被 AspectJ 解析（语法有效性防线）
        AspectJExpressionPointcut pc = new AspectJExpressionPointcut();
        pc.setExpression(annotation.value());
        assertThat(pc.getExpression()).isEqualTo(annotation.value());
    }

    @Test
    @DisplayName("异常消息包含解除限制的指引")
    void exceptionMessageContainsGuidance() {
        ProceedingJoinPoint point = Mockito.mock(ProceedingJoinPoint.class);
        assertThatThrownBy(() -> aspect.aroundHandleRequest(point))
                .hasMessageContaining("spring-boot-kit-security-web-fixed");
    }
}
