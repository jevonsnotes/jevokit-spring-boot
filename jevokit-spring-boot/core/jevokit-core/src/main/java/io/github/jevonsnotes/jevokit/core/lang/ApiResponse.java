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

    /**
     * 构造成功空响应（status=SUCCESS，message=statusText=Success，data 为 null）
     */
    public ApiResponse() {
        this.status = 0L;
        this.statusText = "Success";
        this.message = this.statusText;
    }

    /**
     * 按显式状态构造（message 与 statusText 联动为同一值）
     *
     * @param status  状态码，约定常量见 {@link #SUCCESS}/{@link #FAIL}/{@link #WARN}/{@link #BUSY}
     * @param message 状态消息
     * @param data    业务数据，可为 null
     */
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

    /**
     * 构造成功空响应（等价于无参构造）
     *
     * @param <M> data 类型
     * @return 成功响应实例（data 为 null）
     */
    public static <M> ApiResponse<M> ok() {
        return new ApiResponse<>();
    }

    /**
     * 构造携带数据的成功响应
     *
     * @param data 业务数据；为 Exception 时转失败态、为 Optional 时自动拆包（见单参构造器）
     * @param <T> data 类型
     * @return 成功响应实例
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data);
    }

    /**
     * 构造失败响应
     *
     * @param message 失败消息
     * @param <T>     data 类型
     * @return status=FAIL 的响应（data 为 null）
     */
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(-1L, message, null);
    }

    /**
     * 构造携带数据的失败响应
     *
     * @param message 失败消息
     * @param data    附带的业务数据
     * @param <T>     data 类型
     * @return status=FAIL 的响应
     */
    public static <T> ApiResponse<T> fail(String message, T data) {
        return new ApiResponse<>(-1L, message, data);
    }

    /**
     * 构造告警响应（业务可继续但需人工关注）
     *
     * @param message 告警消息
     * @param <T>     data 类型
     * @return status=WARN 的响应（data 为 null）
     */
    public static <T> ApiResponse<T> warn(String message) {
        return new ApiResponse<>(-2L, message, null);
    }

    /**
     * 构造携带数据的告警响应
     *
     * @param message 告警消息
     * @param data    附带的业务数据
     * @param <T>     data 类型
     * @return status=WARN 的响应
     */
    public static <T> ApiResponse<T> warn(String message, T data) {
        return new ApiResponse<>(-2L, message, data);
    }
}
