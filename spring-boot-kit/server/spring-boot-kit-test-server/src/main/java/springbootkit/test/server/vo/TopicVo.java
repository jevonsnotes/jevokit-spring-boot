package springbootkit.test.server.vo;

import lombok.Data;

import java.util.UUID;

/**
 * @Description TODO
 * @Date 2024/7/3 17:27
 * @Version 0.0.1
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
