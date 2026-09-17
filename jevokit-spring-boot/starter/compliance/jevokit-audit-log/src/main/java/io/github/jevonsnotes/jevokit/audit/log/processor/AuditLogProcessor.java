package io.github.jevonsnotes.jevokit.audit.log.processor;

import cn.hutool.core.util.ReflectUtil;
import org.slf4j.MDC;
import io.github.jevonsnotes.jevokit.audit.log.dto.AuditLogDto;

import java.lang.reflect.Field;

/**
 * @Description 默认审计日志处理器
 * @Date 2022/6/28 9:37
 * @Version 0.0.1
 */
public interface AuditLogProcessor {

    /**
     * 获取用户代码
     * @return
     */
    String getUserCode();

    /**
     * 获取机构代码
     * @return
     */
    String getComCode();


    /**
     * 保存记录
     * @param dto
     */
    void save(AuditLogDto dto);

    /**
     * 植入mdc变量，可在logback配置文件中使用 %X{变量名} 输出
     * @param dto
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
     * 清除mdc变量
     * @param dto
     */
    default void removeMdc(AuditLogDto dto){
        Field[] declaredFields = dto.getClass().getDeclaredFields();
        for(Field field : declaredFields){
            MDC.remove(field.getName());
        }
    }


}
