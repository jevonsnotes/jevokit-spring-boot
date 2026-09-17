package io.github.jevonsnotes.jevokit.api.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;
import io.github.jevonsnotes.jevokit.api.consts.ApiError;

/**
 * JSecure 协议处理异常：协议链路（终端准入/验签/防重放/解密加密）校验不通过时抛出，
 * 携带协议级错误码（默认 {@link ApiError#FAIL}），供统一异常处理转写为 {@code JSecureResponse} 应答。
 * 使用方式：业务侧一般不主动构造；需要主动拒绝终端时可抛本异常并自定义 code/message。
 * <p>关键约束：code 为协议码字段（String），与 message 一并由 Lombok @Data 暴露只读语义的 getter。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class JSecureApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private String code;
    private String message;
    /** 无参构造（code/message 为 null）。 */
    public JSecureApiException() {
        super();
    }
    /**
     * 指定协议码构造。
     *
     * @param code    协议码（写入 code 字段）
     * @param message 提示消息
     */
    public JSecureApiException(final String code,final String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
    /**
     * 默认 FAIL 码构造。
     *
     * @param message 提示消息
     */
    public JSecureApiException(final String message) {
        super(message);
        this.code = ApiError.FAIL.code();
        this.message = message;
    }

    /**
     * 默认 FAIL 码构造（携带原因链）。
     *
     * @param message 提示消息
     * @param e       原因异常
     */
    public JSecureApiException(final String message,Throwable e) {
        super(message,e);
        this.code = ApiError.FAIL.code();
        this.message = message;
    }
}
