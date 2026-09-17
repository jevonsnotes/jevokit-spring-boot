package io.github.jevonsnotes.jevokit.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description TODO
 * @Date 2023/3/10 14:16
 * @Version 0.0.1
 */
@Data
@ConfigurationProperties(prefix = JevokitApiProperties.CONFIG_PREFIX)
public class JevokitApiProperties {
    protected static final String CONFIG_PREFIX = "spkit.api";
    /**
     * 简单接口access限制
     */
    @NestedConfigurationProperty
    private JevokitApiAccessProperties access = new JevokitApiAccessProperties();
    /**
     * 终端配置
     */
    @NestedConfigurationProperty
    private List<JevokitApiClientProperties> client = new ArrayList<>();

}
