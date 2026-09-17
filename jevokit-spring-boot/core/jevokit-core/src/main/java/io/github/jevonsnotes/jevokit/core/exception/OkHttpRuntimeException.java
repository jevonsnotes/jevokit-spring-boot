package io.github.jevonsnotes.jevokit.core.exception;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.util.StrUtil;

/**
 * @Description TODO
 * @Date 2024/9/5
 * @Version 0.0.1
 */
public class OkHttpRuntimeException extends RuntimeException {

    public OkHttpRuntimeException(Throwable e) {
        super(ExceptionUtil.getMessage(e), e);
    }

    public OkHttpRuntimeException(String message) {
        super(message);
    }

    public OkHttpRuntimeException(String messageTemplate, Object... params) {
        super(StrUtil.format(messageTemplate, params));
    }

    public OkHttpRuntimeException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public OkHttpRuntimeException(Throwable throwable, String messageTemplate, Object... params) {
        super(StrUtil.format(messageTemplate, params), throwable);
    }

    /**
     * 导致这个异常的异常是否是指定类型的异常
     *
     * @param clazz 异常类
     * @return 是否为指定类型异常
     */
    public boolean causeInstanceOf(Class<? extends Throwable> clazz) {
        final Throwable cause = this.getCause();
        return null != clazz && clazz.isInstance(cause);
    }
    public static void ofThrow(Throwable e){
        throw new OkHttpRuntimeException(ExceptionUtil.getMessage(e), e);
    }

    public static void ofThrow(String message){
        throw new OkHttpRuntimeException(message);
    }
}
