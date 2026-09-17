package io.github.jevonsnotes.jevokit.sample.vo;

import lombok.Data;

import java.util.UUID;

/**
 * MQ 演示消息体：id（随机）作为顺序消息的 messageGroup 与并发锁键路由目标，
 * type/sendMsg 为演示业务字段。
 */
@Data
public class TopicVo {
    private String id = UUID.randomUUID().toString();
    /**
     * 请求类型
     */
    private Integer type;
    /**
     * 回滚事务
     */
    private boolean transactionRollback;
    /**
     * 异步/同步
     */
    private boolean async;
    /**
     * 发送消息内容
     */
    private String sendMsg;
    /**
     * 循环次数
     */
    private int loop;
    private long sleepTime;
}
