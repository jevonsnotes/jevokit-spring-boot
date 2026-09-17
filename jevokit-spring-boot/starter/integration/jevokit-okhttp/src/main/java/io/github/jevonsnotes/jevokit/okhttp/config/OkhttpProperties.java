package io.github.jevonsnotes.jevokit.okhttp.config;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import io.github.jevonsnotes.jevokit.core.util.CollectionUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description TODO
 * @Date 2024/8/29
 * @Version 0.0.1
 */
@Data
@ConfigurationProperties(prefix = OkhttpProperties.CONFIG_PREFIX)
public class OkhttpProperties {
    protected static final String CONFIG_PREFIX = "spkit.ok.http";
    private Integer connectTimeout = 60;
    private Integer readTimeout = 60;
    private Integer writeTimeout = 60;
    /**
     * 连接池中整体的空闲连接的最大数量
     */
    private Integer maxIdleConnections = 200;
    private ErrorMode errorMode = ErrorMode.throwout;
    /**
     * 连接空闲时间最多为 300 秒
     */
    private Long keepAliveDuration = 300L;
    private List<String> proxyHosts = CollectionUtil.toList("127.0.0.1:8088");
    private String proxyUsername = StrUtil.EMPTY;
    private String proxyPassword = StrUtil.EMPTY;
    private List<String> proxyIgnores = new ArrayList<>();
    private ProxyMode proxyMode = ProxyMode.manual;
    private CheckLevel proxyCheckLevel =  CheckLevel.all;
    private boolean httpStatusSensitive = false;

    /**
     * 代理使用模式
     * always: 默认使用代理，不需要调用ofProxy方法
     * manual: 手动选择代理模式，需要调用ofProxy方法
     */
    public enum ProxyMode {
        always,
        manual
    }

    /**
     * 异常处理模式
     */
    public enum ErrorMode {
        /**
         * 安静模式，不做任何处理
         */
        quiet,
        /**
         * 简单打印日志
         */
        log_simple,
        /**
         * 详细打印日志
         */
        log_detail,
        /**
         * 抛出异常
         */
        throwout
    }

    public enum CheckLevel {
        /**
         * 适用所有级别,即当一个代理出现不可用时,其他uri获取代理时自动剔除该不可用代理
         */
        all,
        /**
         * uri级别,即当一个代理对于某uri来说不可用时,其他uri仍会尝试使用该代理
         */
        uri,

    }
}
