package io.github.jevonsnotes.jevokit.audit.log.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import io.github.jevonsnotes.jevokit.audit.log.processor.AuditLogProcessor;
import io.github.jevonsnotes.jevokit.audit.log.util.AuditLogUtils;
import io.github.jevonsnotes.jevokit.configuration.audit.log.AuditLogProperties;
import io.github.jevonsnotes.jevokit.configuration.config.IpConfiguration;
import io.github.jevonsnotes.jevokit.core.annotation.AuditLog;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

/**
 * 方法返回值脱敏解析器
 */
@Aspect
@Slf4j
@RequiredArgsConstructor
public class AuditLogAspect {
    private final AuditLogProcessor processor;
    private final AuditLogProperties properties;
    private final IpConfiguration ipConfig;
    /**
     * 拦截启用脱敏的方法
     */
    @Pointcut("@annotation(io.github.jevonsnotes.jevokit.core.annotation.AuditLog)")
    public void auditLogPointCut() {
    }


    /**
     * 环绕 @AuditLog 方法：preHandle 时机（around 开启时）与业务完成后各落一次审计，
     * 事件链 ThreadLocal 在 afterHandle 自动回退。
     *
     * @param jp 切点（@AuditLog 标注的方法，事件类型/名称取自注解）
     * @return 业务原返回值（审计不加工业务结果）
     */
    @Around("auditLogPointCut()")
    public Object doAround(ProceedingJoinPoint jp) throws Throwable {
        if(!properties.isEnabled() || !AuditLogUtils.needToPrecess(properties, processor)){
            return jp.proceed();
        }
        final ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        final HttpServletRequest request = servletRequestAttributes.getRequest();
        final HttpServletResponse response = servletRequestAttributes.getResponse();
        final Method method = getMethod(jp);

        AuditLog auditLog = getMethodAnnotation(jp);
        AuditLogUtils.preHandle(()->{
            AuditLogUtils.save(auditLog.eventType(), auditLog.eventName(),request, response,ipConfig, processor,properties,method);
        },properties);
        Object res;
        try {
            res = jp.proceed();
        } finally {
            AuditLogUtils.afterHandle(()->{
                AuditLogUtils.save(auditLog.eventType(), auditLog.eventName(),request, response,ipConfig, processor,properties,method);
            },properties);
        }
        return res;

    }


    /**
     * 取方法上的 @AuditLog 注解（事件类型/名称来源）。
     *
     * @param jp 切点
     * @return 注解；非方法切点返回 null
     */
    public AuditLog getMethodAnnotation(JoinPoint jp) {
        final Method method = getMethod(jp);
        if (method != null) {
            return method.getAnnotation(AuditLog.class);
        }
        return null;
    }
    /**
     * 解析切点对应的 Method。
     *
     * @param jp 切点
     * @return 目标方法；非方法切点返回 null
     */
    public Method getMethod(JoinPoint jp) {
        Signature signature = jp.getSignature();
        if (signature instanceof MethodSignature) {
            return ((MethodSignature) jp.getSignature()).getMethod();
        }
        return null;
    }


}
