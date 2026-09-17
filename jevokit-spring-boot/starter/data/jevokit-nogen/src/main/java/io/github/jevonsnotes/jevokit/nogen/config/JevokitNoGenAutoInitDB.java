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
    public void run(ApplicationArguments args) throws Exception {
        log.info("****** 单号服务初始化开始 ******");
        checkAccessToken();
        initTableBizNo();
        log.info("****** 单号服务初始化完成 ******");
    }

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
    private void checkAccessToken(){
        if(properties.isEnableApi() && StrUtil.isEmpty(properties.getAccessToken())){
            log.warn("单号服务API访问密钥未配置,建议配置 spkit.nogen.access-token！");
        }
    }

}
