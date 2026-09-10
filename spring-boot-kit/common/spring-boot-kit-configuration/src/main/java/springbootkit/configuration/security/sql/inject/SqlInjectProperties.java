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
