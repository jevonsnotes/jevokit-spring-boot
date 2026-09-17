package io.github.jevonsnotes.jevokit.api.consts;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Description TODO
 * @Date 2025/4/10
 * @Version 0.0.1
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
