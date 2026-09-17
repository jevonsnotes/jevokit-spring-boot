package io.github.jevonsnotes.jevokit.desensitization.aspect;

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
import io.github.jevonsnotes.jevokit.core.util.SecurityUtil;
import io.github.jevonsnotes.jevokit.desensitization.annotation.Desensitization;
import io.github.jevonsnotes.jevokit.desensitization.annotation.DstProperty;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstType;
import io.github.jevonsnotes.jevokit.desensitization.desensitizer.DefaultDesensitizer;
import io.github.jevonsnotes.jevokit.desensitization.dto.DstContext;
import io.github.jevonsnotes.jevokit.desensitization.interceptor.SensitizationPowerInterceptor;
import io.github.jevonsnotes.jevokit.desensitization.util.DesUtil;

/**
 * 方法返回值脱敏解析器
 */
@Aspect
@Slf4j
public class DesensitizationAspect {
    @Autowired
    private SensitizationPowerInterceptor sensitizationPowerInterceptor;


    /**
     * 拦截启用脱敏的方法
     */
//    @Pointcut("@annotation(io.github.jevonsnotes.jevokit.desensitization.annotation.Desensitization) || @annotation(io.github.jevonsnotes.jevokit.desensitization.annotation.DstProperty) ")
    @Pointcut("@annotation(io.github.jevonsnotes.jevokit.desensitization.annotation.Desensitization) ")
    public void desensitizationPointCut() {
    }


    /**
     * 环绕启用脱敏的方法：先执行业务，再对返回值脱敏（方法级 @DstProperty 作为无字段标注时的兜底规则）。
     *
     * @param jp 切点（@Desensitization 标注的方法）
     * @return 业务原值经脱敏处理后的结果
     */
    @Around("desensitizationPointCut()")
    public Object doAround(ProceedingJoinPoint jp) throws Throwable {
        Object res = jp.proceed();
        final DstProperty methodDes = getMethodDes(jp);

        return desensitize(res,methodDes);
    }


    /**
     * 取方法级 @DstProperty 标注（作为返回值统一兜底规则）。
     *
     * @param jp 切点
     * @return 方法标注；非方法切点或无标注返回 null
     */
    public DstProperty getMethodDes(JoinPoint jp) {
        Signature signature = jp.getSignature();
        if (signature instanceof MethodSignature) {
            return ((MethodSignature) jp.getSignature()).getMethod().getAnnotation(DstProperty.class);
        }
        return null;
    }
    /**
     * 取方法级 @Desensitization 标注。
     *
     * @param jp 切点
     * @return 方法标注；非方法切点或无标注返回 null
     */
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
