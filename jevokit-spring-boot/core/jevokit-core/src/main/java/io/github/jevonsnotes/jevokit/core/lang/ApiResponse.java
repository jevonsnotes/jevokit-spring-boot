package io.github.jevonsnotes.jevokit.core.lang;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Optional;

public class ApiResponse<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final int SUCCESS = 0;
    public static final int FAIL = -1;
    public static final int WARN = -2;
    public static final int BUSY = -100;
    public static final String SUCCESS_TEXT = "Success";
    public static final String FAIL_TEXT = "Fail";
    public static final String BUSY_TEXT = "Busy";
    private long status;
    private String statusText;
    @Getter
    private String message;
    private Long timestamp = System.currentTimeMillis();
    @Setter
    @Getter
    private T data;

    public ApiResponse() {
        this.status = 0L;
        this.statusText = "Success";
        this.message = this.statusText;
    }

    public ApiResponse(long status, String message, T data) {
        this.status = status;
        this.statusText = message;
        this.data = data;
        this.message = this.statusText;
    }

    public ApiResponse(T data) {
        if (data instanceof Exception) {
            this.status = -1L;
            this.statusText = ((Exception)data).getLocalizedMessage();
        } else {
            this.status = 0L;
            this.statusText = "Success";
            if (data instanceof Optional) {
                Optional<T> opt = (Optional)data;
                if (opt.isPresent()) {
                    this.data = opt.get();
                }
            } else {
                this.data = data;
            }
        }

        this.message = this.statusText;
    }

    public long getStatus() {
        return this.status;
    }

    public void setStatus(long status) {
        this.status = status;
        if (status == 0L) {
            this.statusText = "Success";
        } else if (status == -100L) {
            this.statusText = "Busy";
        } else {
            this.statusText = "";
        }

    }

    /**
     * 获取状态信息
     * @return statusText
     * @deprecated use getMessage instead
     */
    @Deprecated
    public String getStatusText() {
        return this.statusText;
    }

    /**
     * 获取状态信息
     * @param message
     * @deprecated use getMessage instead
     */
    @Deprecated
    public void setStatusText(String message) {
        this.statusText = message;
        this.message = this.statusText;
    }

    public void setMessage(String message) {
        this.message = message;
        this.statusText = this.message;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public static <M> ApiResponse<M> ok() {
        return new ApiResponse<>();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(-1L, message, null);
    }

    public static <T> ApiResponse<T> fail(String message, T data) {
        return new ApiResponse<>(-1L, message, data);
    }

    public static <T> ApiResponse<T> warn(String message) {
        return new ApiResponse<>(-2L, message, null);
    }

    public static <T> ApiResponse<T> warn(String message, T data) {
        return new ApiResponse<>(-2L, message, data);
    }
}
