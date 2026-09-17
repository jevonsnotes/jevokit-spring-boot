package io.github.jevonsnotes.jevokit.nogen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

/**
 * @Description TODO
 * @Date 2023/3/10 14:16
 * @Version 0.0.1
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = JevokitNoGenProperties.CONFIG_PREFIX)
public class JevokitNoGenProperties {
    protected static final String CONFIG_PREFIX = "spkit.nogen";
    private String accessToken;
    private boolean enableApi;
}
