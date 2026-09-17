package io.github.jevonsnotes.jevokit.core.lang;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Optional;

/**
 * 统一 REST 响应信封：status（0 成功，负数异常态）+ statusText/message + data + timestamp。
 * <p>常用入口：静态工厂 {@link #ok()} / {@link #ok(Object)} / {@link #fail(String)} / {@link #warn(String)}。
 * <p>关键语义：{@link #ApiResponse(Object)} 单参构造自带智能分支——入参为 Exception 时自动转失败态
 * （status=FAIL，message 取异常本地化消息），入参为 Optional 时自动拆包（空 Optional 得到 data 为 null 的成功态）；
 * setStatus 会按约定状态码同步 statusText（0→Success、-100→Busy、其余置空），message/statusText 始终联动同值。
 */
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

    /**
     * 按数据类型智能构造：Exception 转失败态，Optional 自动拆包（空 Optional 视为成功但 data 为 null），其余成功态
     *
     * @param data 业务数据，可为 Exception/Optional
     */
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

    /**
     * 设置状态码并联动 statusText（0→Success、-100→Busy、其余置空串）
     *
     * @param status 状态码，约定常量见 {@link #SUCCESS}/{@link #FAIL}/{@link #WARN}/{@link #BUSY}
     */
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
