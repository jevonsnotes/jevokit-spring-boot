package io.github.jevonsnotes.jevokit.api.config;

import lombok.Data;

/**
 * api访问token配置
 */
@Data
public class JevokitApiClientPrintProperties {
    /**
     * 打印请求密文
     */
    private boolean requestCiphertext = true;
    /**
     * 打印请求明文
     */
    private boolean requestPlaintext;
    /**
     * 打印返回密文
     */
    private boolean responseCiphertext = true;
    /**
     * 打印返回明文
     */
    private boolean responsePlaintext;

}
