package springbootkit.configuration.security.pathtraversal;

import cn.hutool.core.collection.CollectionUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description TODO
 * @Date 2022/5/24 11:32
 * @Version 0.0.1
 */
@Data
@RefreshScope
@ConfigurationProperties(PathTraversalProperties.PT_CONFIG_PREFIX)
public class PathTraversalProperties {
    public static final String PT_CONFIG_PREFIX = "spkit.security.path-traversal";
    private boolean enabled;
    private List<String> urlPatterns = new ArrayList<>();
    private List<String> urlExcludePatterns = new ArrayList<>();
    /**
     * 污染风险识别表达式
     */
    private List<String> additionTaintedPatterns;
    private Mode mode = Mode.reject;

    public enum Mode {
        clear,
        reject;
    }


    public List<String> getAllTaintedPatterns(){
        List<String> copy = new ArrayList<>();
        CollectionUtil.addAllIfNotContains(copy, PathTraversalTaintedList.TAINTED_PATTERN_LIST);
        if(CollectionUtil.isNotEmpty(additionTaintedPatterns)){
            CollectionUtil.addAllIfNotContains(copy, additionTaintedPatterns);
        }
        return copy;
    }
}
