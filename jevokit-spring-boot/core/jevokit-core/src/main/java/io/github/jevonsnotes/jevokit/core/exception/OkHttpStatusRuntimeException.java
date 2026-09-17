package io.github.jevonsnotes.jevokit.core.exception;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;

/**
 * HTTP 响应状态异常：响应码非预期（如非 2xx）时抛出，并保留响应上下文供调用方决策。
 * <p>常用入口：静态 {@link #ofThrow(Integer, String, String, String)} 直接抛出；
 * 语义化字段经 getter 获取——{@code getCode()} 响应码、{@code getUrl()} 请求地址、{@code getBody()} 响应体，
 * {@code getMessage()} 为四元拼接串（code message url body），需要结构化信息请用字段而非解析消息。
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
