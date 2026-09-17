package io.github.jevonsnotes.jevokit.core.exception;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.util.StrUtil;

/**
 * HTTP 传输层 IO 异常：连接超时、网络中断等 IO 失败的运行时包装。
 * <p>归入 {@link OkHttpRuntimeException} 异常族，调用方可统一 catch 父类后再用
 * {@code causeInstanceOf} 区分 IO 失败与状态异常。构造器语义与父类一致（支持 {} 模板串）。
 */
public class IORuntimeException extends OkHttpRuntimeException {

    public IORuntimeException(Throwable e) {
        super(ExceptionUtil.getMessage(e), e);
    }

    public IORuntimeException(String message) {
        super(message);
    }

    public IORuntimeException(String messageTemplate, Object... params) {
        super(StrUtil.format(messageTemplate, params));
    }

    public IORuntimeException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public IORuntimeException(Throwable throwable, String messageTemplate, Object... params) {
        super(StrUtil.format(messageTemplate, params), throwable);
    }

    public static void ofThrow(Throwable e){
        throw new IORuntimeException(ExceptionUtil.getMessage(e), e);
    }
}
