package io.github.jevonsnotes.jevokit.rocketmq.lang;

 /**
 * 异步发送回调契约：onSuccess/onException 分别承接发送成败，泛型对应响应与异常类型
 * （kit 层默认实现打日志，业务可自带实现做补偿/重投）。
 */
public interface SendCallback<R,E> {
    void onSuccess(R response);
    void onException(E exception);
}
