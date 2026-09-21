package io.github.jevonsnotes.jevokit.configuration.security.xss;

import cn.hutool.core.collection.CollectionUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.ArrayList;
import java.util.List;

/**
 * XSS 防护配置（jevokit.security.xss）：
 * enabled 总开关；urlPatterns/urlExcludePatterns 以 Ant 风格路径圈定拦截与豁免范围；
 * mode 三态处置——clear 清除命中片段（默认）/ escape 转义 / reject 拒绝；
 * ignoreResponse 默认 true（只处理请求方向，响应方向跳过）；trimText 控制文本首尾去空白；
 * additionTaintedPatterns 追加识别正则（默认污染表 {@link XssTaintedList} 为空，规则完全由用户配置）。
 * 方法级可用 @XssClean 注解覆盖本配置。
 */
@Data
@RefreshScope
@ConfigurationProperties(XssProperties.XSS_CONFIG_PREFIX)
public class XssProperties {
    public static final String XSS_CONFIG_PREFIX = "jevokit.security.xss";
    /** 总开关，默认关闭 */
    private boolean enabled;
    /** 文本首尾去空白 */
    private boolean trimText;
    /** 拦截的 URL 范围（Ant 风格），空则不圈定 */
    private List<String> urlPatterns = new ArrayList<>();
    /** 豁免的 URL 范围（Ant 风格），优先于 urlPatterns */
    private List<String> urlExcludePatterns = new ArrayList<>();
    /** 附加识别正则，叠加在默认空表之上 */
    private List<String> additionTaintedPatterns = new ArrayList<>();
    /** 处置模式，默认 clear */
    private Mode mode = Mode.clear;
    /** 是否跳过响应方向的处理，默认 true */
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

    /**
     * 合并默认污染表与附加规则
     *
     * @return 去重合并后的正则列表（新副本，修改不影响内部状态）
     */
    public List<String> getAllTaintedPatterns(){
        List<String> copy = new ArrayList<>();
        CollectionUtil.addAllIfNotContains(copy, XssTaintedList.TAINTED_PATTERN_LIST);
        if(CollectionUtil.isNotEmpty(additionTaintedPatterns)){
            CollectionUtil.addAllIfNotContains(copy, additionTaintedPatterns);
        }
        return copy;
    }

}
