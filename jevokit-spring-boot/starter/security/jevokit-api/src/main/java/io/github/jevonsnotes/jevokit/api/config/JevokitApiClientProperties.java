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
     * 上传摘要算法允许范围（终端级）：可选项 sha256/sm3/md5。缺省空 = 协议全集（三种皆可）；
     * 一旦配置则 fail-closed：条目 digestAlg 不在范围内即拒绝（提示带允许清单）——
     * 例如配置 [sm3] 后客户端以 sha256 声明摘要将被拒。
     */
    private List<String> uploadDigestAlgorithms = new ArrayList<>();
    /**
     * 上传摘要校验开关（终端级）：缺省 true；置 false 后跳过以下三处摘要校验——
     * ① push 文件 part 的摘要比对（服务端按 digestAlg 重算与声明值比对）；
     * ② pull 拉取下载后的摘要比对；
     * ③ push 条目 digest 字段必填要求（可不声明摘要）。
     * 仅供可信内网或大文件算摘要代价高的场景评估后关闭——关闭后传输完整性仅剩 size 一道防线
     * （若 size 校验也关闭则无任何内容校验）。
     */
    private boolean uploadDigestVerify = true;
    /**
     * 上传大小校验开关（终端级）：缺省 true；置 false 后仅跳过"声明与实际一致性"校验——
     * ① push 条目 size 必填与"声明值 = 文件 part 实际字节数"比对；
     * ② pull 拉取下载后"实际字节数 = 声明值"比对。
     * 注意：upload-max-size 上限不受此开关影响（始终生效——声明值与实际字节数任一超限即拒，
     * 上限是终端资源保护，配多少就是多少）；pull 下载时按上限封顶读流的内存自保同样始终生效。
     */
    private boolean uploadSizeVerify = true;
    /**
     * pull 模式允许拉取的域名白名单（SSRF 硬防线）：支持精确域名（files.partner.com）与通配子域
     * （*.partner.com）。缺省空且有 pull 请求即拒绝（fail-fast，不静默放行）。
     */
    private List<String> uploadFetchUrlWhitelist = new ArrayList<>();

    /** 上传单文件大小缺省上限：50MB。 */
    public static final long DEFAULT_UPLOAD_MAX_SIZE = 50L * 1024 * 1024;

    /**
     * 单请求上传文件条目数上限（终端级，push/pull/refer 合计）：防"N 个单文件均合法"的聚合
     * 流量/临时盘放大（单文件受 upload-max-size 约束，条目数无上限则总量无界）。
     * 缺省 20（保守值）；置 0 或负数 = 不限制。
     */
    private int uploadMaxFileCount = DEFAULT_UPLOAD_MAX_FILE_COUNT;

    /** 单请求文件条目数缺省上限。 */
    public static final int DEFAULT_UPLOAD_MAX_FILE_COUNT = 20;
    /**
     * 空则使用secretKey
     * @return 返回密钥
     */
    public String getSecretKeySm4() {
        return StrUtil.isBlank(secretKeySm4) ? this.secretKey : this.secretKeySm4;
    }
}
