package io.github.jevonsnotes.jevokit.rocketmq.consts;

/**
 * 发送形态四分类：普通 / 延迟（delayLevel 档位）/ 顺序（FIFO，按 messageGroup 分区）/
 * 事务——asyncSend 全参形态据此路由底层调用。
 */
public enum TopicMsgType {
    NORMAL,DELAY,FIFO,TRANSACTION
}
