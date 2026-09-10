package springbootkit.desensitization.aspect;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import springbootkit.core.util.SecurityUtil;
import springbootkit.desensitization.annotation.Desensitization;
import springbootkit.desensitization.annotation.DstProperty;
import springbootkit.desensitization.consts.DstType;
import springbootkit.desensitization.desensitizer.DefaultDesensitizer;
import springbootkit.desensitization.dto.DstContext;
import springbootkit.desensitization.interceptor.SensitizationPowerInterceptor;
import springbootkit.desensitization.util.DesUtil;

/**
 * @Description 方法返回值脱敏解析器
 * @Date 2022/7/11 15:12
 * @Version 0.0.1
 */
@Aspect
@Slf4j
public class DesensitizationAspect {
    @Autowired
    private SensitizationPowerInterceptor sensitizationPowerInterceptor;


    /**
     * 拦截启用脱敏的方法
     */
//    @Pointcut("@annotation(springbootkit.desensitization.annotation.Desensitization) || @annotation(springbootkit.desensitization.annotation.DstProperty) ")
    @Pointcut("@annotation(springbootkit.desensitization.annotation.Desensitization) ")
    public void desensitizationPointCut() {
    }


    @Around("desensitizationPointCut()")
    public Object doAround(ProceedingJoinPoint jp) throws Throwable {
        Object res = jp.proceed();
        final DstProperty methodDes = getMethodDes(jp);

        return desensitize(res,methodDes);
    }


    public DstProperty getMethodDes(JoinPoint jp) {
        Signature signature = jp.getSignature();
        if (signature instanceof MethodSignature) {
            return ((MethodSignature) jp.getSignature()).getMethod().getAnnotation(DstProperty.class);
        }
        return null;
    }
    public Desensitization getMethodEnableDes(JoinPoint jp) {
        Signature signature = jp.getSignature();
        if (signature instanceof MethodSignature) {
            return ((MethodSignature) jp.getSignature()).getMethod().getAnnotation(Desensitization.class);
        }
        return null;
    }

    private Object desensitize(Object obj, DstProperty defaultDstProperty) {

        return SecurityUtil.filterStr(obj, (o, field, s) -> {
            DstProperty dstProperty;
            if (StrUtil.isNotBlank(s)) {
                String fieldName = field == null ? null : field.getDeclaringClass().getName() + "." +field.getName();
//                String fieldName = field == null ? null : field.getName();
                dstProperty = (field == null || field.getAnnotation(DstProperty.class) == null) ? (defaultDstProperty) : field.getAnnotation(DstProperty.class);

                if (dstProperty != null) {
                    if (!dstProperty.ignore()) {
                        DstContext dstContext = DstContext.builder().type(dstProperty.type()).level(dstProperty.level()).build();

                        if (dstProperty.type().equals(DstType.CUSTOM)
                                && !dstProperty.desensitizer().equals(DefaultDesensitizer.class)) {
                            try {
                                s = dstProperty.desensitizer().newInstance().des(o, field, s,dstContext);
                            } catch (InstantiationException | IllegalAccessException e) {
                                log.debug(e.getMessage(), e);
                            }
                        } else {
                            s = DesUtil.des(s, dstContext);
                        }
                    }
                } else {
                    s = DesUtil.desByProperties(fieldName, s);
                }
            }
            return s;
        });
    }

}
