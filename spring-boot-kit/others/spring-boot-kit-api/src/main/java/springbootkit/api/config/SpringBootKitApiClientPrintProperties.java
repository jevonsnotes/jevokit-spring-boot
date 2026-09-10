package springbootkit.api.config;

import lombok.Data;

/**
 * @Description api访问token配置
 * @Date 2023/3/10 14:16
 * @Version 0.0.1
 */
@Data
public class SpringBootKitApiClientPrintProperties {
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
