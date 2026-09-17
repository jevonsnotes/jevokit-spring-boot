package io.github.jevonsnotes.jevokit.api.advice;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 * @Description 返回体加密适配器
 * @Date 2025/4/10
 * @Version 0.0.1
 */
@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class EncryptResponseAdviceAdapter implements ResponseBodyAdvice<Object>, Ordered {
    private final JevokitApiProperties properties;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.hasMethodAnnotation(JSecureApi.class)
                && returnType.getGenericParameterType() instanceof ParameterizedType
                && ClassUtil.isClassAssignable(JSecureResponse.class,((ParameterizedType)returnType.getGenericParameterType()).getRawType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        try {
            if (body instanceof JSecureResponse<?> resp) {
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
            JSecureContext.remove();
        } catch (Exception e) {
            throw new JSecureApiException(ApiError.FAIL.code(), e.getMessage());
        }


        return body;
    }

    /**
     * 异常处理
     *
     * @param e 捕捉jSecure接口异常、@valid校验异常
     * @return 接口返回体
     */
    @ResponseBody
    @ExceptionHandler({JSecureApiException.class, MethodArgumentNotValidException.class, Exception.class})
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

    private void printCiphertext(JSecureResponse<?> response) {
        JevokitApiClientProperties client = JSecureContext.getCurrentClient();
        if (log.isDebugEnabled() || (client != null && client.getPrint().isResponseCiphertext())) {
            log.info("{}, response ciphertext content: {}", JSecureContext.getLogPrefix(), JSON.toJSONString(response));
        }

    }

    private void printPlaintext(JSecureResponse<?> response) {
        JevokitApiClientProperties client = JSecureContext.getCurrentClient();
        if (log.isDebugEnabled() || (client != null && client.getPrint().isResponsePlaintext())) {
            log.info("{}, response plaintext content: {}", JSecureContext.getLogPrefix(), JSON.toJSONString(response));
        }
    }

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
    @Override
    public int getOrder() {
        return 100;
    }
}
