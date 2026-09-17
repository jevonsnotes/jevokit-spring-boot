package io.github.jevonsnotes.jevokit.nogen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

 /**
 * nogen 配置项（spkit.nogen）：accessToken 为 /jevokit/nogen/** API 的访问令牌
 * （请求头 accessToken 携带，NoGenInterceptor 校验）；enableApi 开关决定是否暴露发号 API
 * （默认 false，仅本地 BzNoHelper 调用）。支持 @RefreshScope 热更新。
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = JevokitNoGenProperties.CONFIG_PREFIX)
public class JevokitNoGenProperties {
    protected static final String CONFIG_PREFIX = "spkit.nogen";
    private String accessToken;
    private boolean enableApi;
}
