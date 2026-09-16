package springbootkit.audit.log.aspect;

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
import springbootkit.audit.log.processor.AuditLogProcessor;
import springbootkit.audit.log.util.AuditLogUtils;
import springbootkit.configuration.audit.log.AuditLogProperties;
import springbootkit.configuration.config.IpConfiguration;
import springbootkit.core.annotation.AuditLog;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

/**
 * @Description 方法返回值脱敏解析器
 * @Date 2022/7/11 15:12
 * @Version 0.0.1
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
    @Pointcut("@annotation(springbootkit.core.annotation.AuditLog)")
    public void auditLogPointCut() {
    }


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


    public AuditLog getMethodAnnotation(JoinPoint jp) {
        final Method method = getMethod(jp);
        if (method != null) {
            return method.getAnnotation(AuditLog.class);
        }
        return null;
    }
    public Method getMethod(JoinPoint jp) {
        Signature signature = jp.getSignature();
        if (signature instanceof MethodSignature) {
            return ((MethodSignature) jp.getSignature()).getMethod();
        }
        return null;
    }


}
