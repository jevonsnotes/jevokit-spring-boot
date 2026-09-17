package io.github.jevonsnotes.jevokit.api.config;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description api访问token配置
 * @Date 2023/3/10 14:16
 * @Version 0.0.1
 */
@Data
public class JevokitApiClientProperties {
    /**
     * 客户端标识，用于绑定公密钥、盐值等信息
     */
    private String id;
    /**
     * 是否启用客户端
     */
    boolean enabled = true;
    /**
     * 是否启用请求解密
     */
    boolean decrypt = true;
    /**
     * 是否启用返回加密
     */
    boolean encrypt = true;
    /**
     * 是否启用验签
     */
    boolean verify = true;
    /**
     * 是否启用防重放
     * 默认基于单节点实现，
     */
    boolean preventReplay = true;



    /**
     * 密钥(其他密钥为空时默认取该密钥)
     */
    private String secretKey;
    /**
     * 密钥
     */
    private String secretKeySm4;
    /**
     * 公钥，用于需要公钥解密、验签等场景
     */
    private String publicKey;
    /**
     * 私钥，用于需要私钥加密、加签等场景
     */
    private String privateKey;
    /**
     * 打印
     */
    @NestedConfigurationProperty
    private JevokitApiClientPrintProperties print = new JevokitApiClientPrintProperties();
    /**
     * 请求头前缀
     */
    @NestedConfigurationProperty
    private JevokitApiHeaderProperties header = new JevokitApiHeaderProperties();
    /**
     * 允许访问的路径
     */
    private List<String> allowPath = new ArrayList<>();
    /**
     * 允许访问的客户端ip
     */
    private List<String> allowIp = new ArrayList<>();
    /**
     * 空则使用secretKey
     * @return 返回密钥
     */
    public String getSecretKeySm4() {
        return StrUtil.isBlank(secretKeySm4) ? this.secretKey : this.secretKeySm4;
    }
}
