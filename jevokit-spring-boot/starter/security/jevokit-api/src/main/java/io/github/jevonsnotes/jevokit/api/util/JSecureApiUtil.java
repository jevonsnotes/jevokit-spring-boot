package io.github.jevonsnotes.jevokit.api.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.MD5;
import cn.hutool.crypto.digest.SM3;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiProperties;
import io.github.jevonsnotes.jevokit.api.consts.ApiHSignAlg;
import io.github.jevonsnotes.jevokit.api.dto.JSRequestExecutor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Description TODO
 * @Date 2025/4/14
 * @Version 0.0.1
 */
@Slf4j
public class JSecureApiUtil {
    private static JevokitApiProperties properties;

    /**
     * 创建一个请求执行器
     * @param data 请求体
     * @param responseDataClass 返回体类
     * @return 返回对象
     * @param <T> 请求体类
     * @param <R>返回体类
     */
    public static <T,R> JSRequestExecutor<T,R> getExecutor(String clientId, String url, T data, Class<R> responseDataClass){
        return JSRequestExecutor.create(clientId,url,data,responseDataClass);
    }

    public JSecureApiUtil(final JevokitApiProperties properties) {
        JSecureApiUtil.properties = properties;
    }

    public static JevokitApiClientProperties getClient(String clientId) {
        return properties.getClient()
                .stream()
                .filter(jevokitApiClientProperties -> jevokitApiClientProperties.getId().equals(clientId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No client found with id " + clientId));
    }

    public static JevokitApiClientProperties getCurrentClient() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attr != null) {
            HttpServletRequest request = attr.getRequest();
            Set<String> clientHeaders = properties.getClient()
                    .stream()
                    .map(properties -> ApiUtil.rebuildHeader(properties.getHeader().getClientIdName(), properties.getHeader().getPrefix()))
                    .collect(Collectors.toSet());
            // 获取终端
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                if (clientHeaders.contains(name)) {
                    String clientId = request.getHeader(name);
                    List<JevokitApiClientProperties> clients = properties.getClient().stream().filter(jevokitApiClientProperties -> jevokitApiClientProperties.getId().equals(clientId)).collect(Collectors.toList());
                    if (!clients.isEmpty()) {
                        return clients.get(0);
                    }
                }
            }
        }
        return null;
    }



    public static Optional<HttpServletRequest> getCurrentRequest(){
        if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes){
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attr != null) {
                return Optional.of(attr.getRequest());
            }
        }
        return Optional.empty();
    }

    public static String rebuildHeader(String key, String prefix) {
        return prefix.endsWith("-") ? prefix + key : prefix + "-" + key;
    }

    public static boolean checkHeaderSignature(String signature, ApiHSignAlg alg, String timestamp, String nonce, String secretKey) {
        String headerSignActual = headerSignature(alg, timestamp, nonce, secretKey);
        boolean result = headerSignActual.equals(signature);
        if(!result){
            log.error("请求头验签失败 - expected {} actual {}", signature, headerSignActual);
        }
        return result;
    }

    public static String headerSignature(ApiHSignAlg alg, String timestamp, String nonce, String secretKey) {
        String headerSignActual = StrUtil.EMPTY;
        String plaintext = timestamp + secretKey + nonce;
        if (ApiHSignAlg.MD5.equals(alg)) {
            headerSignActual = MD5.create().digestHex(plaintext);
        } else if (ApiHSignAlg.SHA256.equals(alg)) {
            headerSignActual = SecureUtil.sha256().digestHex(plaintext);
        } else if (ApiHSignAlg.SM3.equals(alg)) {
            headerSignActual = SM3.create().digestHex(plaintext);
        }
        return headerSignActual;
    }


}

