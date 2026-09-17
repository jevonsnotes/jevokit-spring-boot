package io.github.jevonsnotes.jevokit.audit.log.processor;

import cn.hutool.core.util.ReflectUtil;
import org.slf4j.MDC;
import io.github.jevonsnotes.jevokit.audit.log.dto.AuditLogDto;

import java.lang.reflect.Field;

/**
 * 默认审计日志处理器
 */
public interface AuditLogProcessor {

    /**
     * 获取当前用户代码（审计归属维度；默认实现为占位值 DEF_USER_CODE，业务应覆盖）。
     *
     * @return 用户代码；空/占位值时配合 ignore-non-user 跳过审计
     */
    String getUserCode();

    /**
     * 获取当前机构代码（审计归属维度；默认实现为占位值 DEF_COM_CODE）。
     *
     * @return 机构代码
     */
    String getComCode();


    /**
     * 保存审计记录（按持久化模式落库/落日志，见 DefaultAuditLogProcessor）。
     *
     * @param dto 组装完成的审计记录
     */
    void save(AuditLogDto dto);

    /**
     * 把审计记录的全部非空字段植入 MDC（logback 以 %X{字段名} 输出，串起业务日志与审计）。
     *
     * @param dto 审计记录（字段名即 MDC 键）
     */
    default void mdc(AuditLogDto dto){
        Field[] declaredFields = dto.getClass().getDeclaredFields();
        for(Field field : declaredFields){
            Object value = ReflectUtil.getFieldValue(dto, field);
            if(value != null){
                MDC.put(field.getName(), value.toString());
            }
        }
    }

    /**
     * 清除 {@link #mdc(AuditLogDto)} 植入的 MDC 变量（必须成对调用防线程串染）。
     *
     * @param dto 审计记录
     */
    default void removeMdc(AuditLogDto dto){
        Field[] declaredFields = dto.getClass().getDeclaredFields();
        for(Field field : declaredFields){
            MDC.remove(field.getName());
        }
    }


}
