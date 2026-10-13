package io.github.jevonsnotes.jevokit.api.advice;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.text.AntPathMatcher;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import io.github.jevonsnotes.jevokit.api.annotation.JSecureApi;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiProperties;
import io.github.jevonsnotes.jevokit.api.context.JSecureContext;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;
import io.github.jevonsnotes.jevokit.api.service.IPreventReplayAttack;
import io.github.jevonsnotes.jevokit.api.util.ApiUtil;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;
import io.github.jevonsnotes.jevokit.core.util.ClassUtil;
import io.github.jevonsnotes.jevokit.core.util.CollectionUtil;
import io.github.jevonsnotes.jevokit.core.util.HttpUtil;
import io.github.jevonsnotes.jevokit.core.util.SecurityUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import java.util.Optional;


/**
 * 请求体解密适配器
 */

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class DecryptRequestAdviceAdapter extends RequestBodyAdviceAdapter {
    private final JevokitApiProperties properties;
    private final IPreventReplayAttack iPreventReplayAttack;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    /**
     * 仅处理标注 @JSecureApi 且参数为 JSecureRequest 泛型的方法（协议入口识别）。
     *
     * @return true 表示该请求体走解密验签链路
     */
    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return methodParameter.hasMethodAnnotation(JSecureApi.class)
                && targetType instanceof ParameterizedType
                && ClassUtil.isClassAssignable(JSecureRequest.class,((ParameterizedType) targetType).getRawType());
    }

    /**
     * 读体前的协议处理：解析报文 → 终端准入（存在/路径/IP）→ 防重放 → 请求头验签 → 数据解密验签 →
     * 明文反序列化为 target，以新 HttpInputMessage 交还 Spring 继续绑定。
     *
     * @return 替换后的输入消息（内容为填充了 view/target 的协议报文）；校验不通过抛 JSecureApiException
     */
    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        long startTime = System.currentTimeMillis();
        String message = IoUtil.readUtf8(inputMessage.getBody());
        JevokitApiClientProperties client = null;
        JSecureRequest<Object> jsr = null;
        String declaringClass = Objects.requireNonNull(parameter.getMethod()).getDeclaringClass().getName();
        String methodName = parameter.getMethod().getName();
        try {
            JSecureApi jSecureApi = AnnotationUtils.getAnnotation(Objects.requireNonNull(parameter.getMethod()), JSecureApi.class);

            // 放置日志前缀
            JSecureContext.setLogPrefix(String.format("JSecureApi %s#%s", declaringClass, methodName));
            // 转换实体对象
            jsr = JSONObject.parseObject(message, new TypeReference<JSecureRequest<Object>>() {
            });
            // 放置初始变量
            JSecureContext.setRequest(jsr);
            // 获取终端
            client = JSecureContext.getCurrentClientThrowIfInvalid();

            // 校验交易类型
            checkTransType(jsr.getTransType(),Objects.requireNonNull(jSecureApi));

            // 校验路径权限
            Assert.isTrue(checkAllowPath(client), () -> new JSecureApiException("终端无该接口访问权限"));

            // 校验IP准入
            Assert.isTrue(checkAllowIp(client), () -> new JSecureApiException("终端IP无该接口访问权限"));


            // 防重放校验 nonce timestamp
            if (client.isPreventReplay()) {
                Assert.isFalse(iPreventReplayAttack.checkReplay(getNonce(client, inputMessage), getTimestamp(client, inputMessage)), () -> new JSecureApiException("重复请求或已失效，请调整相关访问参数"));
            }
            // 请求头验签
            if (client.isVerify()) {
                verifyHeader(inputMessage, client);
            }

            //获取明文
            if (client.isDecrypt()) {
                decrypt(jsr, client);
            } else {
                jsr.setView(jsr.getData());
            }

            // 请求体验签
            if (client.isVerify()) {
                verifyBody(jsr, client);
            }

            Object o = JSONObject.parseObject(jsr.getView(), ((ParameterizedType) targetType).getActualTypeArguments()[0]);
            jsr.setTarget(o);

            String finalMessage = JSON.toJSONString(jsr);

            ByteArrayInputStream utf8Stream = IoUtil.toUtf8Stream(finalMessage);
            return new HttpInputMessage() {
                /** 解密后的协议报文流（内容为填充 view/target 的 JSON）。 */
                @Override
                public InputStream getBody() {
                    return utf8Stream;
                }

                /** 透传原请求头（内容协商结果不受解密影响）。 */
                @Override
                public HttpHeaders getHeaders() {
                    return inputMessage.getHeaders();
                }
            };
        } finally {

            if (log.isDebugEnabled()) {
                String cost = ((System.currentTimeMillis() - startTime) / 1000d) + "s";
                log.info("{}, decrypt cost: {}", JSecureContext.getLogPrefix(), cost);
            }

            if (log.isDebugEnabled() || (client != null && client.getPrint().isRequestCiphertext())) {
                log.info("{}, request ciphertext content: {}", JSecureContext.getLogPrefix(), message);
            }

            if (log.isDebugEnabled() || (client != null && client.getPrint().isRequestPlaintext())) {
                log.info("{}, request plaintext content: {}", JSecureContext.getLogPrefix(), jsr != null ? JSON.toJSONString(jsr.toPlaintextDto()) : null);
            }
        }

    }

    /**
     * 解密
     *
     * @param jsr    数据体
     * @param client 终端
     */
    private void decrypt(JSecureRequest<Object> jsr, JevokitApiClientProperties client) {
        try {
            jsr.setView(SecurityUtil.sm4cbcDecrypt(jsr.getData(), client.getSecretKeySm4()));
        } catch (Exception e) {
            throw new JSecureApiException("接口解密失败：" + e.getMessage(), e);
        }
    }

    /**
     * 校验接口交易类型
     * @param current
     * @param jSecureApi
     */
    private void checkTransType(String current,JSecureApi jSecureApi){
        boolean match = false;
        String[] types = jSecureApi.value();
        if(ArrayUtil.isNotEmpty(types)){
            for(String cfg : types){
                if (StrUtil.trimToEmpty(cfg).equals(StrUtil.trimToEmpty(current))){
                    match = true;
                    break;
                }
            }
        } else {
            match = true;
        }
        Assert.isTrue(match, () -> new JSecureApiException("接口交易类型不匹配"));
    }
    /**
     * 校验终端访问路径权限
     *
     * @param client 终端信息配置
     * @return 校验结果
     */
    private boolean checkAllowPath(JevokitApiClientProperties client) {
        Optional<HttpServletRequest> currentRequest = JSecureApiUtil.getCurrentRequest();
        if (currentRequest.isPresent()) {
            List<String> allowPath = client.getAllowPath();
            if (CollectionUtil.isNotEmpty(allowPath)) {
                for (String allow : allowPath) {
                    if (antPathMatcher.match(allow, currentRequest.get().getRequestURI())) {
                        return true;
                    }
                }
                return false;
            }
            return true;
        } else {
            log.error("终端访问路径权限校验异常");
            return false;
        }
    }
    /**
     * 校验终端IP访问权限
     *
     * @param client 终端信息配置
     * @return 校验结果
     */
    private boolean checkAllowIp(JevokitApiClientProperties client) {
        Optional<HttpServletRequest> currentRequest = JSecureApiUtil.getCurrentRequest();
        if (currentRequest.isPresent()) {
            List<String> allows = client.getAllowIp();
            if (CollectionUtil.isNotEmpty(allows)) {
                for (String allow : allows) {
                    if (antPathMatcher.match(allow,HttpUtil.getRemoteIp(currentRequest.get()))) {
                        return true;
                    }
                }
                return false;
            }
            return true;
        } else {
            log.error("终端IP访问校验异常");
            return false;
        }
    }

    /**
     * 请求头验签
     *
     * @param inputMessage 输入消息
     * @param client       终端配置
     * @return 验证结果
     */
    private void verifyHeader(HttpInputMessage inputMessage, JevokitApiClientProperties client) {
        String timestamp = getTimestamp(client, inputMessage);
        String nonce = getNonce(client, inputMessage);
        String signature = getSignature(client, inputMessage);
        boolean result = JSecureApiUtil.checkHeaderSignature(signature, client.getHeader().getSignAlg(), timestamp, nonce, client.getSecretKey());
        if (!result) {
            throw new JSecureApiException("请求头验签失败");
        }
    }

    /**
     * 请求体验签
     *
     * @param jsr    请求载体
     * @param client 终端配置
     * @return 验证结果
     */
    private void verifyBody(JSecureRequest<Object> jsr, JevokitApiClientProperties client) {

        //请求体验签
        boolean bodyVerify = SecurityUtil.sm2Base64Verify(jsr.getView(), jsr.getSignature(), client.getPublicKey());
        if (!bodyVerify) {
            log.error("请求体验签失败 - expected {}", jsr.getSignature());
            throw new JSecureApiException("请求体验签失败");
        }
    }

//    private JevokitApiClientProperties getClient(HttpInputMessage inputMessage) {
//        Set<String> clientHeaders = properties.getClient()
//                .stream()
//                .map(properties -> ApiUtil.rebuildHeader(properties.getHeader().getClientIdName(), properties.getHeader().getPrefix()))
//                .collect(Collectors.toSet());
//        String clientId = inputMessage.getHeaders()
//                .entrySet()
//                .stream()
//                .filter(entry -> clientHeaders.contains(entry.getKey()))
//                .findFirst()
//                .orElseThrow(() -> new JSecureApiException("终端信息不存在"))
//                .getValue()
//                .get(0);
////        String clientId = Assert.notNull(inputMessage.getHeaders().get("client-id"),()->new JSecureApiException("终端码不能为空")).get(0);
//        List<JevokitApiClientProperties> clients = properties.getClient()
//                .stream()
//                .filter(jevokitApiClientProperties -> jevokitApiClientProperties.getId().equals(clientId))
//                .collect(Collectors.toList());
//        return clients.isEmpty() ? null : clients.get(0);
//    }

    /**
     * 取请求头时间戳（头名按终端前缀拼接；缺失抛协议异常，防重放与验签都依赖它）。
     */
    private String getTimestamp(JevokitApiClientProperties client, HttpInputMessage inputMessage) {
        List<String> headers = inputMessage.getHeaders().get(ApiUtil.rebuildHeader(client.getHeader().getTimestampName(), client.getHeader().getPrefix()));
        Assert.isTrue(CollectionUtil.isNotEmpty(headers), () -> new JSecureApiException("请求头时间戳为空"));
        return headers.get(0);
    }

    /**
     * 取请求头随机码（缺失抛协议异常，防重放与验签都依赖它）。
     */
    private String getNonce(JevokitApiClientProperties client, HttpInputMessage inputMessage) {
        List<String> headers = inputMessage.getHeaders().get(ApiUtil.rebuildHeader(client.getHeader().getNonceName(), client.getHeader().getPrefix()));
        Assert.isTrue(CollectionUtil.isNotEmpty(headers), () -> new JSecureApiException("请求头随机码为空"));
        return headers.get(0);
    }

    /**
     * 取请求头签名值（缺失抛协议异常）。
     */
    private String getSignature(JevokitApiClientProperties client, HttpInputMessage inputMessage) {
        List<String> headers = inputMessage.getHeaders().get(ApiUtil.rebuildHeader(client.getHeader().getSignatureName(), client.getHeader().getPrefix()));
        Assert.isTrue(CollectionUtil.isNotEmpty(headers), () -> new JSecureApiException("请求头签名为空"));
        return headers.get(0);
    }


}
