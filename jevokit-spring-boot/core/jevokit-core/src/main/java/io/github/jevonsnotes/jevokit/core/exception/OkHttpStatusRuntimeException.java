package io.github.jevonsnotes.jevokit.core.exception;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;

/**
 * @Description TODO
 * @Date 2024/9/5
 * @Version 0.0.1
 */


public class OkHttpStatusRuntimeException extends OkHttpRuntimeException {
    @Getter
    private Integer code;
    private String message;
    @Getter
    private String url;

    @Getter
    private String body;

    public OkHttpStatusRuntimeException(Integer code,String message,String url,String body) {
        super(String.format("%s %s %s %s",code,message,url,body));
        this.code = code;
        this.message = message;
        this.url = url;
        this.body = body;
    }
    public OkHttpStatusRuntimeException(Integer code,String message,String url) {
        super(String.format("%s %s %s %s",code,message,url, StrUtil.EMPTY));
        this.code = code;
        this.message = message;
        this.url = url;
        this.body = StrUtil.EMPTY;
    }

    public static void ofThrow(Integer code, String message,String url,String body) {
        throw new OkHttpStatusRuntimeException(code, message,url,body);
    }

}
