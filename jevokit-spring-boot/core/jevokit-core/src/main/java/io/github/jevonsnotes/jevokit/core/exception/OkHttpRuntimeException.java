package io.github.jevonsnotes.jevokit.core.exception;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.util.StrUtil;

/**
 * HTTP 客户端执行异常：OkHttp 调用链抛出的受检异常统一包装为该运行时异常，免除调用方样板 catch。
 * <p>常用入口：静态 {@link #ofThrow(Throwable)} / {@link #ofThrow(String)} 直接抛出；
 * 构造器支持 hutool 风格模板串（messageTemplate 中的 {} 占位符由 {@code StrUtil.format} 填充）；
 * {@link #causeInstanceOf(Class)} 可判断因链根因类型，用于重试等分支决策。
 * 子类：{@link IORuntimeException}（传输层 IO 失败）、{@link OkHttpStatusRuntimeException}（响应状态异常）。
 */
public class OkHttpRuntimeException extends RuntimeException {

    /**
     * 以原异常消息与原因构造
     *
     * @param e 原异常
     */
    public OkHttpRuntimeException(Throwable e) {
        super(ExceptionUtil.getMessage(e), e);
    }

    /**
     * 以固定消息构造
     *
     * @param message 异常消息
     */
    public OkHttpRuntimeException(String message) {
        super(message);
    }

    /**
     * 以模板串构造消息
     *
     * @param messageTemplate 消息模板，{} 占位符按参数顺序填充
     * @param params          填充参数
     */
    public OkHttpRuntimeException(String messageTemplate, Object... params) {
        super(StrUtil.format(messageTemplate, params));
    }

    /**
     * 以消息与原因构造
     *
     * @param message   异常消息
     * @param throwable 原因
     */
    public OkHttpRuntimeException(String message, Throwable throwable) {
        super(message, throwable);
    }

    /**
     * 以原因与模板串构造消息（{} 占位符由 StrUtil.format 填充）
     *
     * @param throwable       原因
     * @param messageTemplate 消息模板
     * @param params          填充参数
     */
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
    /**
     * 直接抛出该异常（抛出型工厂，省去 throw 样板）
     *
     * @param e 原异常
     */
    public static void ofThrow(Throwable e){
        throw new OkHttpRuntimeException(ExceptionUtil.getMessage(e), e);
    }

    /**
     * 直接抛出该异常（抛出型工厂，省去 throw 样板）
     *
     * @param message 异常消息
     */
    public static void ofThrow(String message){
        throw new OkHttpRuntimeException(message);
    }
}
