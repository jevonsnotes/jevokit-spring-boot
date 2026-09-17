package io.github.jevonsnotes.jevokit.audit.log.processor;

import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import io.github.jevonsnotes.jevokit.audit.log.dao.AuditLogDao;
import io.github.jevonsnotes.jevokit.audit.log.dto.AuditLogDto;
import io.github.jevonsnotes.jevokit.configuration.audit.log.AuditLogProperties;

import java.lang.reflect.Field;

/**
 * 审计日志默认处理器：填充占位用户/公司标识并按配置的持久化模式（LOG/DB/MIXED）落审计。
 * 使用方式：@ConditionalOnMissingBean 自动注册；业务实现 {@code AuditLogProcessor}
 * （真实取当前登录用户、MDC 注入等）注册为 bean 即可整体替换。
 * <p>关键约束：默认用户码为占位值 DEF_USER_CODE（kit 不绑定任何身份体系），
 * 配合 ignore-non-user=true 可跳过未登录请求；持久化模式为 LOG/未配置时仅结构化打日志，
 * DB 模式下无 AuditLogDao bean（无 DB 环境）则静默跳过 DB 落库（降级不报错）。
 */
@Slf4j
public class DefaultAuditLogProcessor implements AuditLogProcessor {
    public static final String DEF_USER_CODE = "DEF_USER_CODE";
    public static final String DEF_COM_CODE = "DEF_COM_CODE";

    @Override
    public String getUserCode() {
        return DEF_USER_CODE;
    }

    @Override
    public String getComCode() {
        return DEF_COM_CODE;
    }

    public void save(AuditLogDto dto) {

        AuditLogProperties properties = SpringUtil.getBean(AuditLogProperties.class);
        if(properties != null){
            switch (properties.getPersistent().getMode()){
                case MIXED:
                    processPersistentLog(dto);
                    processPersistentDB(dto);
                    break;
                case DB:
                    processPersistentDB(dto);
                    break;
                case LOG:
                default:
                    processPersistentLog(dto);
            }
        } else {
            processPersistentLog(dto);
        }
    }

    private void processPersistentDB(AuditLogDto dto) {
        AuditLogDao dao = SpringUtil.getBean(AuditLogDao.class);
        if(dao != null) {
            dao.saveOrUpdate(dto);
        }
    }
    private void processPersistentLog(AuditLogDto dto) {
        StrBuilder sb = new StrBuilder();
        Field[] declaredFields = dto.getClass().getDeclaredFields();
        Object[] vals = new Object[declaredFields.length];
        for(int i = 0; i < declaredFields.length; i++){
            Field field = declaredFields[i];
            sb.append("[{}] ");
            vals[i] = ObjectUtil.defaultIfNull(ReflectUtil.getFieldValue(dto,field),"");
        }
        log.info(sb.subString(0,sb.length()-1), vals);
    }
}
