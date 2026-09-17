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
    /**
     * 切点：标注 @Desensitization 的方法（仅方法级标注触发；字段级 @DstProperty 单独存在不触发，
     * 由外层方法标注统一驱动）。
     */
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

    /**
     * 返回值脱敏核心：递归遍历对象字符串值，注解解析优先级为——字段级 @DstProperty 优先，
     * 字段无标注时回退方法级 defaultDstProperty（入参，即切面方法上的 @DstProperty），
     * 两者皆无时按属性名规则兜底（DesUtil.desByProperties）。
     * <p>type=CUSTOM 且指定了非默认脱敏器时反射实例化委托（实例化失败仅 debug 记录并跳过——
     * 防御性降级，脱敏器配置错误不阻断业务返回）；ignore=true 的标注直接跳过。
     *
     * @param obj                业务返回值
     * @param defaultDstProperty 方法级兜底脱敏标注（可 null）
     * @return 脱敏后的返回值
     */
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
