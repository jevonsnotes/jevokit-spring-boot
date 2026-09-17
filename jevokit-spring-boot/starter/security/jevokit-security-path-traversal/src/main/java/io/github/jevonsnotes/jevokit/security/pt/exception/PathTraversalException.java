package io.github.jevonsnotes.jevokit.security.pt.exception;

import io.github.jevonsnotes.jevokit.core.exception.SecurityException;

/**
 * 路径穿越防护拒绝交互异常：reject 模式下入参命中污染规则时由
 * {@link io.github.jevonsnotes.jevokit.security.pt.util.PathTraversalUtil#clean(String, java.util.List)} 抛出，
 * 表示疑似路径穿越报文被整体拒绝（区别于 clear 模式的静默剥离）。
 * 使用方式：属 jevokit 安全异常体系（继承 {@link io.github.jevonsnotes.jevokit.core.exception.SecurityException}），
 * 由全局异常处理统一兜底，业务方一般不捕获。
 */
public class PathTraversalException extends SecurityException {
    /**
     * Constructs a <code>SecurityException</code> with no detail  message.
     */
    public PathTraversalException() {
        super();
    }

    /**
     * Constructs a <code>SecurityException</code> with the specified
     * detail message.
     *
     * @param s the detail message.
     */
    public PathTraversalException(String s) {
        super(s);
    }

    /**
     * Creates a <code>SecurityException</code> with the specified
     * detail message and cause.
     *
     * @param message the detail message (which is saved for later retrieval
     *                by the {@link #getMessage()} method).
     * @param cause   the cause (which is saved for later retrieval by the
     *                {@link #getCause()} method).  (A <tt>null</tt> value is permitted,
     *                and indicates that the cause is nonexistent or unknown.)
     * @since 1.5
     */
    public PathTraversalException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a <code>SecurityException</code> with the specified cause
     * and a detail message of <tt>(cause==null ? null : cause.toString())</tt>
     * (which typically contains the class and detail message of
     * <tt>cause</tt>).
     *
     * @param cause the cause (which is saved for later retrieval by the
     *              {@link #getCause()} method).  (A <tt>null</tt> value is permitted,
     *              and indicates that the cause is nonexistent or unknown.)
     * @since 1.5
     */
    public PathTraversalException(Throwable cause) {
        super(cause);
    }
}
