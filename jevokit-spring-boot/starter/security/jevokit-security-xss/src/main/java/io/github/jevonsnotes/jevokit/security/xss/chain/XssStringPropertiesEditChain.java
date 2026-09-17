package io.github.jevonsnotes.jevokit.security.xss.chain;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.ControllerAdvice;
import io.github.jevonsnotes.jevokit.configuration.security.xss.XssProperties;
import io.github.jevonsnotes.jevokit.core.chain.StringPropertiesSupportChain;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;
import io.github.jevonsnotes.jevokit.security.xss.cleaner.XssCleaner;

/**
 * @Description TODO
 * @Date 2022/5/24 17:44
 * @Version 0.0.1
 */
@Slf4j
@RequiredArgsConstructor
@ControllerAdvice
@ConditionalOnBean(XssCleaner.class)
public class XssStringPropertiesEditChain  implements StringPropertiesSupportChain {

    private final XssCleaner xssCleaner;
    private final XssProperties properties;


    @Override
    public String getTextEdit(String text) {
        return text;
    }

    @Override
    public String setTextEdit(String text) {
        String newer = text;
        if (ConfigHolder.getXssMeta().isEnabled()) {
            newer = xssCleaner.clean(properties.isTrimText()? StrUtil.trim(newer) : newer);
            log.debug("Request parameter value:{} cleaned up by xss defending, current value is:{}.", text, newer);
        } else {
            newer = properties.isTrimText()? StrUtil.trim(newer) : newer;
        }
        return newer;
    }
}
