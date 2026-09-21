package io.github.jevonsnotes.jevokit.nogen.config;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import io.github.jevonsnotes.jevokit.core.util.ExceptionUtil;
import io.github.jevonsnotes.jevokit.nogen.dao.NoGenDao;

 /**
 * 初始化
 */
@Slf4j
@RequiredArgsConstructor
public class JevokitNoGenAutoInitDB implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final NoGenDao dao;
    private final JevokitNoGenProperties properties;
    /**
     * Callback used to run the bean.
     *
     * @param args incoming application arguments
     * @throws Exception on error
     */
    @Override
    /**
    * 启动期自动建表/校验（ApplicationRunner）：探测 biz_no 表不存在则按库种方言建表。
    * @param args 启动参数
    * @throws Exception 建表 SQL 执行失败
    */
    public void run(ApplicationArguments args) throws Exception {
        log.info("****** 单号服务初始化开始 ******");
        checkAccessToken();
        initTableBizNo();
        log.info("****** 单号服务初始化完成 ******");
    }

    /**
    * 建表探测：方言正则匹配"表不存在"才执行 DDL（已存在零动作）。
    */
    private void initTableBizNo(){
        try {
            jdbcTemplate.queryForList(dao.validation());
        } catch (DataAccessException e) {
            if(ExceptionUtil.loopMatchMessage(e,dao.getPattenNotExists())){
                jdbcTemplate.execute(dao.getCreateSqlBizTable());
            } else {
                throw e;
            }
        }
    }
    /**
    * 启动校验：enableApi 开启但 accessToken 为空时告警（不阻断启动）。
    */
    private void checkAccessToken(){
        if(properties.isEnableApi() && StrUtil.isEmpty(properties.getAccessToken())){
            log.warn("单号服务API访问密钥未配置,建议配置 jevokit.nogen.access-token！");
        }
    }

}
