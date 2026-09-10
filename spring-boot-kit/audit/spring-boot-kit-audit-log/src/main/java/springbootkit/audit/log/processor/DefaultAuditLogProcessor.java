package springbootkit.audit.log.processor;

import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import springbootkit.audit.log.dao.AuditLogDao;
import springbootkit.audit.log.dto.AuditLogDto;
import springbootkit.configuration.audit.log.AuditLogProperties;

import java.lang.reflect.Field;

/**
 * @Description TODO
 * @Date 2022/6/28 9:39
 * @Version 0.0.1
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
