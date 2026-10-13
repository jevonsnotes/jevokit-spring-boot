package io.github.jevonsnotes.jevokit.api.advice;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import io.github.jevonsnotes.jevokit.api.annotation.JSecureApi;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiProperties;
import io.github.jevonsnotes.jevokit.api.consts.ApiError;
import io.github.jevonsnotes.jevokit.api.context.JSecureContext;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.exception.JSecureApiException;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;
import io.github.jevonsnotes.jevokit.core.util.ClassUtil;
import io.github.jevonsnotes.jevokit.core.util.SecurityUtil;

import java.lang.reflect.ParameterizedType;
import java.util.Optional;

/**
 * 返回体加密适配器
 */
@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class EncryptResponseAdviceAdapter implements ResponseBodyAdvice<Object>, Ordered {
    private final JevokitApiProperties properties;

    /**
     * 仅处理 JSecureResponse 返回类型（协议出口识别）。
     *
     * @return true 表示该响应走加密签名链路
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.hasMethodAnnotation(JSecureApi.class)
                && returnType.getGenericParameterType() instanceof ParameterizedType
                && ClassUtil.isClassAssignable(JSecureResponse.class,((ParameterizedType)returnType.getGenericParameterType()).getRawType());
    }

    /**
     * 序列化前的协议处理：业务实体转密文 data（终端开启 encrypt 时）、SM2 签名，剥离 view/target 明文字段。
     *
     * @return 协议化的响应对象；body 非 JSecureResponse 时原样返回
     */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        try {
            if (body instanceof JSecureResponse<?>) {
                JSecureResponse<?> resp = (JSecureResponse<?>) body;
                fillTransInfo(JSecureContext.getRequest(), resp);

                if (resp.getTarget() != null) {
                    String target = JSON.toJSONString(resp.getTarget());
                    resp.setView(target);

                    JevokitApiClientProperties client = Optional.ofNullable(JSecureContext.getCurrentClient()).orElseThrow(() -> new JSecureApiException("终端获取异常，请联系管理员"));

                    String signature = SecurityUtil.sm2Base64Sign(target, client.getPrivateKey());
                    resp.setSignature(signature);
                    if (client.isEncrypt()) {
                        resp.setData(SecurityUtil.sm4cbcEncrypt(target, client.getSecretKeySm4()));
                    } else {
                        resp.setData(target);
                    }
                    resp.setTarget(null);
                }
                printCiphertext(resp.toCiphertextDto());
                printPlaintext(resp.toPlaintextDto());
                return resp;
            }
        } catch (Exception e) {
            throw new JSecureApiException(ApiError.FAIL.code(), e.getMessage());
        }


        return body;
    }


    @ResponseBody
    @ExceptionHandler({JSecureApiException.class, MethodArgumentNotValidException.class, Exception.class})
    /**
     * 异常兜底转协议响应（配合 ControllerAdvice 场景使用）。
     *
     * @param e 业务或协议异常
     * @return FAIL 状态的协议响应
     */
    public JSecureResponse<?> handler(Exception e) throws Exception {
        try {
            JSecureRequest<?> jsr = JSecureContext.getRequest();
            if (jsr == null && !JSecureContext.isMethodArgumentNotValidException(e)) {
                throw e;
            }
            setResponseCharset();
            JSecureResponse<Object> resp = null;
            if (e instanceof JSecureApiException) {
                resp = JSecureResponse.fail(((JSecureApiException) e).getCode(), e.getMessage());
            } else if (e instanceof MethodArgumentNotValidException) {
                FieldError fieldError = ((MethodArgumentNotValidException) e).getFieldError();
                if (fieldError != null) {
                    resp = JSecureResponse.fail(ApiError.FAIL.code(), fieldError.getDefaultMessage());
                }
            }
            resp = (resp == null ? JSecureResponse.fail(ApiError.FAIL.code(), e.getMessage()) : resp);
            fillTransInfo(jsr, resp);
            printCiphertext(resp.toCiphertextDto());
            printPlaintext(resp.toPlaintextDto());
            return resp;
        } finally {
            JSecureContext.remove();
        }
    }

    /**
     * 按需打印响应密文（debug 或终端 print.response-ciphertext 开启时，带协议日志前缀）。
     */
    private void printCiphertext(JSecureResponse<?> response) {
        JevokitApiClientProperties client = JSecureContext.getCurrentClient();
        if (log.isDebugEnabled() || (client != null && client.getPrint().isResponseCiphertext())) {
            log.info("{}, response ciphertext content: {}", JSecureContext.getLogPrefix(), JSON.toJSONString(response));
        }

    }

    /**
     * 按需打印响应明文（debug 或终端 print.response-plaintext 开启时；仅密文形态出网，此处仅供排障）。
     */
    private void printPlaintext(JSecureResponse<?> response) {
        JevokitApiClientProperties client = JSecureContext.getCurrentClient();
        if (log.isDebugEnabled() || (client != null && client.getPrint().isResponsePlaintext())) {
            log.info("{}, response plaintext content: {}", JSecureContext.getLogPrefix(), JSON.toJSONString(response));
        }
    }

    /**
     * 对齐请求编码设置响应字符集（请求未声明时兜底 UTF-8，防密文/签名因编码漂移损坏）。
     */
    private void setResponseCharset() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attr != null) {
                HttpServletRequest request = attr.getRequest();
                HttpServletResponse response = attr.getResponse();

                if (response != null) {
                    String characterEncoding = request.getCharacterEncoding();
                    response.setCharacterEncoding(StrUtil.blankToDefault(characterEncoding, "UTF-8"));
                }
            }
        }
    }

    /**
     * 回填交易信息：响应未显式指定 transNo/transType 时继承请求值（对端可按交易号关联请求响应）。
     */
    private void fillTransInfo(JSecureRequest<?> jsr, JSecureResponse<?> resp) {
        if (jsr != null) {
            if (StrUtil.isBlank(resp.getTransNo())) {
                resp.setTransNo(jsr.getTransNo());
            }
            if (StrUtil.isBlank(resp.getTransType())) {
                resp.setTransType(jsr.getTransType());
            }
        }
    }

    /**
     * 实现ordered接口，异常处理器根据order来判断处理顺序，如果都为空则order值为 Ordered.LOWEST_PRECEDENCE,参考OrderComparator.compare方法
     *
     * @return 顺序值
     */
    /** 本适配器在响应链中的执行序（先于常规 JSON 处理）。 */
    @Override
    public int getOrder() {
        return 100;
    }
}
