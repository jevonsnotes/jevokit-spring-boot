package io.github.jevonsnotes.jevokit.security.sql.inject.chain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.jevonsnotes.jevokit.configuration.security.pathtraversal.PathTraversalProperties;
import io.github.jevonsnotes.jevokit.configuration.security.sql.inject.SqlInjectProperties;
import io.github.jevonsnotes.jevokit.core.chain.StringPropertiesSupportChain;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;
import io.github.jevonsnotes.jevokit.security.sql.inject.util.SqlInjectUtil;

/**
 * @Description TODO
 * @Date 2022/5/24 16:04
 * @Version 0.0.1
 */
@Slf4j
@RequiredArgsConstructor
public class SqlInjectStringPropertiesEditChain implements StringPropertiesSupportChain {

    private final SqlInjectProperties properties;

    @Override
    public String getTextEdit(String content) {
        return content;
    }

    @Override
    public String setTextEdit(String content) {
        String newer = content;
        if(ConfigHolder.getSIMeta().isEnabled()) {
            newer = SqlInjectUtil.clean(content,properties.getAllTaintedPatterns());
            log.debug("Request parameter value:{} cleaned up by sql-inject defending, current value is:{}.", content, newer);
        }
        return newer;
    }
}
