package io.github.jevonsnotes.jevokit.api.consts;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * JSecure API 协议应答状态码枚举：{@code JSecureResponse.code} 的协议级取值。
 * 使用方式：仅表示协议层成败（成功 0 / 失败 999）；业务级细分错误码由业务方在 message 或自定义扩展中表达，
 * 不要往本枚举追加业务语义的码值。
 */

@AllArgsConstructor
public enum ApiError {
    SUCCESS("0","成功"),
    FAIL("999","失败");

    private final String code;
    private final String msg;

    public String code() {
        return this.code;
    }
    public String msg() {
        return this.msg;
    }
}
