package springbootkit.api.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;
import springbootkit.api.consts.ApiError;

/**
 * @Description TODO
 * @Date 2025/4/10
 * @Version 0.0.1
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class JSecureApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private String code;
    private String message;
    public JSecureApiException() {
        super();
    }
    public JSecureApiException(final String code,final String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
    public JSecureApiException(final String message) {
        super(message);
        this.code = ApiError.FAIL.code();
        this.message = message;
    }

    public JSecureApiException(final String message,Throwable e) {
        super(message,e);
        this.code = ApiError.FAIL.code();
        this.message = message;
    }
}
