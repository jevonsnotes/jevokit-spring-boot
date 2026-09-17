package io.github.jevonsnotes.jevokit.security.web.fixed.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;


/**
 * fixed security problem CVE-2016-1000027
 */
@Aspect
@Slf4j
public class SpringWebFixedAspect {

    /**
     * 设置AOP切点
     */
    @Pointcut("execution(* org.springframework.remoting.httpinvoker.HttpInvokerServiceExporter.handleRequest(..))")
    public void springWebFixedPointcut() {
    }

    /**
     * 执行方法环绕
     *
     * @param point
     */
    @Around(value = "springWebFixedPointcut()")
    public Object aroundHandleRequest(ProceedingJoinPoint point) throws Throwable {
        log.info("CVE-2016-1000027 invoking. Already prevented by jevokit-security-web-fixed-spring-boot-starter");
        throw new RuntimeException("禁用org.springframework.remoting.httpinvoker.HttpInvokerServiceExporter.handleRequest，防止CVE-2016-1000027安全漏洞，如需解除限制，请排除 jevokit-security-web-fixed-spring-boot-starter 包");
    }
}
