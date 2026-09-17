package io.github.jevonsnotes.jevokit.configuration.security.xss;

import cn.hutool.core.collection.CollectionUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.ArrayList;
import java.util.List;

@Data
@RefreshScope
@ConfigurationProperties(XssProperties.XSS_CONFIG_PREFIX)
public class XssProperties {
    public static final String XSS_CONFIG_PREFIX = "spkit.security.xss";
    private boolean enabled;
    private boolean trimText;
    private List<String> urlPatterns = new ArrayList<>();
    private List<String> urlExcludePatterns = new ArrayList<>();
    private List<String> additionTaintedPatterns = new ArrayList<>();
    private Mode mode = Mode.clear;
    private boolean ignoreResponse = true;

    public enum Mode {
        /**
         * 清除模式
         */
        clear,
        /**
         * 转义模式
         */
        escape,
        /**
         * 拒绝访问
         */
        reject;
    }

    public List<String> getAllTaintedPatterns(){
        List<String> copy = new ArrayList<>();
        CollectionUtil.addAllIfNotContains(copy, XssTaintedList.TAINTED_PATTERN_LIST);
        if(CollectionUtil.isNotEmpty(additionTaintedPatterns)){
            CollectionUtil.addAllIfNotContains(copy, additionTaintedPatterns);
        }
        return copy;
    }

}
