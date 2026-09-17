package io.github.jevonsnotes.jevokit.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JSecure API 模块总配置（前缀 spkit.api）：简单接口 token 准入与接入终端（client）清单。
 * 使用方式：access 配置路径级访问令牌（JevokitApiAccessInterceptor 校验）；client 列表登记每个调用方终端的
 * 密钥对、SM4 密钥、加解密开关与请求头签名配置，服务端按请求头中的终端识别码路由到对应配置。
 * <p>关键约束：client 列表是解密/验签的信任锚，密钥属敏感配置，建议配合 jasypt（ENC 密文）落库。
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
