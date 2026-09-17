package io.github.jevonsnotes.jevokit.api.config;

import lombok.Data;
import io.github.jevonsnotes.jevokit.api.consts.ApiHSignAlg;

/**
 * @Description api访问token配置
 * @Date 2023/3/10 14:16
 * @Version 0.0.1
 */
@Data
public class JevokitApiHeaderProperties {
    /**
     * 请求头名称前缀
     */
    private String prefix = "x-api";
    /**
     * 请求头名称 签名
     */
    private String signatureName = "signature";
    /**
     * 请求头名称 时间戳
     */
    private String timestampName = "timestamp";
    /**
     * 请求头名称 随机码
     */
    private String nonceName = "nonce";
    /**
     * 请求头名称 终端识别码
     */
    private String clientIdName = "client-id";

    private ApiHSignAlg signAlg = ApiHSignAlg.MD5;
}
