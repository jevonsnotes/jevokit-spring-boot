package io.github.jevonsnotes.jevokit.workflow.util;

import cn.hutool.core.util.ReflectUtil;
import io.github.jevonsnotes.jevokit.workflow.annotation.FlowVar;
import io.github.jevonsnotes.jevokit.workflow.consts.FlowScope;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @Description TODO
 * @Date 2023/5/11 18:04
 * @Version 0.0.1
 */
public class FlowUtil {
    public static <T>Map<String,Object> getVarsLocal(T vo){
        return getVars(vo,false);
    }
    public static <T>Map<String,Object> getVarsGlobal(T vo){
        return getVars(vo,true);
    }

    public static <T>Map<String,Object> getVars(T vo,boolean isGlobal){
        Map<String,Object> vars = new HashMap<>();
        Set<String> histories = new HashSet<>();
        Field[] fields = ReflectUtil.getFieldsDirectly(vo.getClass(),true);
        for (Field field : fields) {
            if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                FlowVar annotation = field.getAnnotation(FlowVar.class);
                if (annotation != null
                        && annotation.scope().equals(isGlobal ? FlowScope.GLOBAL : FlowScope.LOCAL)
                        && !histories.contains(field.getName())) {
                    Object value = ReflectUtil.getFieldValue(vo, field);
                    if (value != null) {
                        vars.put(field.getName(), value);
                    } else if(!annotation.ignoreIfNull() && String.class.isAssignableFrom(field.getType())) {
                        vars.put(field.getName(),null);
                    }
                    histories.add(field.getName());
                }
            }
        }
        return vars;
    }
}
