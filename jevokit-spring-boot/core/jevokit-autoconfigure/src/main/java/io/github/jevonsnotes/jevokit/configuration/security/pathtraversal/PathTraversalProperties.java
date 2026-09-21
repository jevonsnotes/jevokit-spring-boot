package io.github.jevonsnotes.jevokit.configuration.security.pathtraversal;

import cn.hutool.core.collection.CollectionUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.ArrayList;
import java.util.List;

/**
 * 路径穿越防护配置（jevokit.security.path-traversal）：
 * enabled 总开关；urlPatterns/urlExcludePatterns 以 Ant 风格路径圈定拦截与豁免的 URL 范围；
 * additionTaintedPatterns 在默认污染表（{@link PathTraversalTaintedList}）之上追加识别正则。
 * <p>注意默认处置模式为 <b>reject</b>（命中即拒绝整条请求），与 SQL 注入/XSS 默认的 clear 相反——
 * 路径穿越误清洗会改变文件语义，宁可拒绝；方法级可用 @PtClean 注解覆盖本配置。
 */
@Data
@RefreshScope
@ConfigurationProperties(PathTraversalProperties.PT_CONFIG_PREFIX)
public class PathTraversalProperties {
    public static final String PT_CONFIG_PREFIX = "jevokit.security.path-traversal";
    /** 总开关，默认关闭 */
    private boolean enabled;
    /** 拦截的 URL 范围（Ant 风格），空则不圈定 */
    private List<String> urlPatterns = new ArrayList<>();
    /** 豁免的 URL 范围（Ant 风格），优先于 urlPatterns */
    private List<String> urlExcludePatterns = new ArrayList<>();
    /**
     * 污染风险识别表达式
     */
    private List<String> additionTaintedPatterns;
    /** 处置模式，默认 reject */
    private Mode mode = Mode.reject;

    /** 处置模式：clear 清除命中片段后放行；reject 拒绝整条请求 */
    public enum Mode {
        clear,
        reject;
    }


    /**
     * 合并默认污染表与附加规则
     *
     * @return 去重合并后的正则列表（新副本，修改不影响内部状态）
     */
    public List<String> getAllTaintedPatterns(){
        List<String> copy = new ArrayList<>();
        CollectionUtil.addAllIfNotContains(copy, PathTraversalTaintedList.TAINTED_PATTERN_LIST);
        if(CollectionUtil.isNotEmpty(additionTaintedPatterns)){
            CollectionUtil.addAllIfNotContains(copy, additionTaintedPatterns);
        }
        return copy;
    }
}
