package io.github.jevonsnotes.jevokit.audit.log.processor;

import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
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

    /** 占位用户码（kit 不绑定身份体系，业务覆盖本处理器提供真实值）。 */
    @Override
    public String getUserCode() {
        return DEF_USER_CODE;
    }

    /** 占位机构码（同 getUserCode）。 */
    @Override
    public String getComCode() {
        return DEF_COM_CODE;
    }

    /**
     * 按持久化模式分发：LOG 仅结构化日志、DB 落审计表（无 DAO 时静默跳过）、MIXED 两者兼做。
     *
     * @param dto 审计记录
     */
    public void save(AuditLogDto dto) {

        AuditLogProperties properties = beanIfAvailable(AuditLogProperties.class);
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

    /**
     * DB 落库：无 AuditLogDao bean（无 DB 环境）时静默跳过（降级不报错）。
     */
    private void processPersistentDB(AuditLogDto dto) {
        AuditLogDao dao = beanIfAvailable(AuditLogDao.class);
        if(dao != null) {
            dao.saveOrUpdate(dto);
        }
    }
    /**
     * 日志落盘：反射取全部字段按 [{}] 占位结构化输出（AUDIT_LOG 主题）。
     */
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

    /**
     * 存在即取的容器查找：bean 缺失时返回 null 而非抛 {@code NoSuchBeanDefinitionException}。
     * <p>遗留 #1 复核：原 {@code SpringUtil.getBean} 的 {@code if(x != null)} 判定是死分支——
     * hutool getBean 缺 bean 即抛异常，类注释承诺的"无 AuditLogDao（无 DB 环境）静默跳过降级"
     * 实际不可达（DB 模式配了持久化但无 JdbcTemplate 时每个审计请求都会炸）。改
     * {@code getBeanProvider().getIfAvailable()} 使既有判空降级真正生效，行为与文档一致。
     * <p>线程定性：本处理器仅由审计过滤器/切面在请求线程调用（容器刷新完成后），查询本身
     * 不构成 DEF-1 等待环，属降级契约修复而非死锁加固。
     */
    private static <T> T beanIfAvailable(Class<T> type) {
        ApplicationContext context = SpringUtil.getApplicationContext();
        return context == null ? null : context.getBeanProvider(type).getIfAvailable();
    }
}
