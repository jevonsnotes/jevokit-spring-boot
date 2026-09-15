package springbootkit.configuration.security.sql.inject;

import cn.hutool.core.collection.CollectionUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.ArrayList;
import java.util.List;

@Data
@RefreshScope
@ConfigurationProperties(SqlInjectProperties.CONFIG_PREFIX)
public class SqlInjectProperties {
    public static final String CONFIG_PREFIX = "spkit.security.sql-inject";
    private boolean enabled;
    private List<String> urlPatterns = new ArrayList<>();
    private List<String> urlExcludePatterns = new ArrayList<>();
    private Mode mode = Mode.clear;

    /**
     * 附加污染风险识别表达式（正则，clear 模式逐条 replaceAll 清除、reject 模式逐条 matches 命中即拒绝，
     * 追加在默认污染表之后生效）。
     * <p>默认污染表保持保守（仅 4 条：UPDATE..SET / INSERT INTO / DELETE FROM / (AND|OR|WHERE) 等式，
     * 见 {@link SqlTaintedList}）；union select、注释截断等激进规则不进默认表，需要时按下例 opt-in：
     * <pre>
     * spkit:
     *   security:
     *     sql-inject:
     *       addition-tainted-patterns: # 激进防护 opt-in（默认不启用；示例正则均有单测背书）
     *         - ((?i)union[ ]+select)   # union 组合查询注入
     *         - '--'                    # 行注释截断（标准 SQL 方言）
     *         - '#'                     # 行注释截断（MySQL 方言；yaml 中必须加引号，防被当作注释）
     *         - [/][*].*?[*][/]         # 块注释截断（非贪婪；块注释的转义斜杠形态在 javadoc 中用字符类形态表示）
     * </pre>
     * 适用场景：内部管理端等可信度较高的入口可整体启用；公开面向用户的入口需评估误清洗风险——
     * clear 模式会剥离命中片段（如 "C#"、话题 "#标签"、区间 "1--5"），reject 模式直接拒绝整条报文。
     */
    private List<String> additionTaintedPatterns;


    public enum Mode {
        /**
         * 清除模式
         */
        clear,

        reject;
    }

    public List<String> getAllTaintedPatterns(){
        List<String> copy = new ArrayList<>();
        CollectionUtil.addAllIfNotContains(copy,SqlTaintedList.TAINTED_PATTERN_LIST);
        if(CollectionUtil.isNotEmpty(additionTaintedPatterns)){
            CollectionUtil.addAllIfNotContains(copy, additionTaintedPatterns);
        }
        return copy;
    }

}
