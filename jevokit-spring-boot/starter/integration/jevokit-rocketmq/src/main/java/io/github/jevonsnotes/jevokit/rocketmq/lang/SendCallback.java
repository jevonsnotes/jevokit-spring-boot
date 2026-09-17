package io.github.jevonsnotes.jevokit.rocketmq.lang;

/**
 * @Description TODO
 * @Date 2022/5/25 8:34
 * @Version 0.0.1
 */
public interface SendCallback<R,E> {
    void onSuccess(R response);
    void onException(E exception);
}
