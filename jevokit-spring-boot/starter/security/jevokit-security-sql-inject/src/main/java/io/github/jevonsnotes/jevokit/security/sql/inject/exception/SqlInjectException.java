package io.github.jevonsnotes.jevokit.security.sql.inject.exception;

import io.github.jevonsnotes.jevokit.core.exception.SecurityException;

/**
 * @Description TODO
 * @Date 2022/5/24 14:52
 * @Version 0.0.1
 */
public class SqlInjectException extends SecurityException {
    /**
     * Constructs a <code>SecurityException</code> with no detail  message.
     */
    public SqlInjectException() {
        super();
    }

    /**
     * Constructs a <code>SecurityException</code> with the specified
     * detail message.
     *
     * @param s the detail message.
     */
    public SqlInjectException(String s) {
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
    public SqlInjectException(String message, Throwable cause) {
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
    public SqlInjectException(Throwable cause) {
        super(cause);
    }
}
