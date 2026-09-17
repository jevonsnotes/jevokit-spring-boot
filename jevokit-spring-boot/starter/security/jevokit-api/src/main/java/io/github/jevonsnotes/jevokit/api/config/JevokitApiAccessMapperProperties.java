package io.github.jevonsnotes.jevokit.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description api访问token配置
 * @Date 2023/3/10 14:16
 * @Version 0.0.1
 */
@Data
public class JevokitApiAccessMapperProperties {
    private String path;
    private String token;

}
