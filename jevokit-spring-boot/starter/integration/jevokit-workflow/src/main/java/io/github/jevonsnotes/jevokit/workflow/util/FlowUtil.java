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
 * 流程变量提取器：反射读取 VO 实例上 @FlowVar 标注的字段，按 scope 归类产出变量表——
 * getVarsLocal 任务级（LOCAL）/ getVarsGlobal 实例级（GLOBAL），供流程启动与任务办结写入。
 *
 * <p>关键约束：静态与 final 字段跳过；同名子类/父类字段以先遇者（子类）为准去重；
 * String 字段的 null 默认显式写入（覆盖引擎中的旧值），非 String 的 null 跳过——
 * 差异源于 ignoreIfNull 只对 String 放宽（见 @FlowVar 定义处）。
 */
public class FlowUtil {
/**
    * 提取 LOCAL 域流程变量（任务级）。
    * @param vo 带 @FlowVar 声明的载体对象
    * @return LOCAL 域变量表（仅含非静态/非 final、scope 匹配的实例字段）
    */
    public static <T>Map<String,Object> getVarsLocal(T vo){
        return getVars(vo,false);
    }
/**
    * 提取 GLOBAL 域流程变量（实例级）。
    * @param vo 带 @FlowVar 声明的载体对象
    * @return GLOBAL 域变量表
    */
    public static <T>Map<String,Object> getVarsGlobal(T vo){
        return getVars(vo,true);
    }

/**
    * 提取器实现：按 scope 归类反射收集；String 字段 null 显式写入（覆盖旧值），非 String null 跳过。
    * @param vo 载体对象
    * @param isGlobal true 取 GLOBAL 域，false 取 LOCAL 域
    * @return 对应域的变量表（字段名 → 字段值）
    */
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
