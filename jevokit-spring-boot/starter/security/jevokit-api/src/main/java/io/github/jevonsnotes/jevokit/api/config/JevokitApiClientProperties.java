package io.github.jevonsnotes.jevokit.api.config;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * api访问token配置
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
     * 上传文件大小上限（字节）：单文件 push 元数据预检与 pull 下载预检共用，缺省 50MB（保守值）。
     */
    private long uploadMaxSize = DEFAULT_UPLOAD_MAX_SIZE;
    /**
     * 上传文件格式白名单（终端级）：key = fileType（如 invoice/id_card），value = 允许的 format 列表
     * （如 [pdf,jpg,png]）。缺省空 Map = 不限制；一旦配置则 fail-closed：条目 fileType 必须有映射且
     * format 在列表内，否则拒绝。
     */
    private Map<String, List<String>> uploadFormatWhitelist = new HashMap<>();
    /**
     * pull 模式允许拉取的域名白名单（SSRF 硬防线）：支持精确域名（files.partner.com）与通配子域
     * （*.partner.com）。缺省空且有 pull 请求即拒绝（fail-fast，不静默放行）。
     */
    private List<String> fetchUrlWhitelist = new ArrayList<>();

    /** 上传单文件大小缺省上限：50MB。 */
    public static final long DEFAULT_UPLOAD_MAX_SIZE = 50L * 1024 * 1024;
    /**
     * 空则使用secretKey
     * @return 返回密钥
     */
    public String getSecretKeySm4() {
        return StrUtil.isBlank(secretKeySm4) ? this.secretKey : this.secretKeySm4;
    }
}
