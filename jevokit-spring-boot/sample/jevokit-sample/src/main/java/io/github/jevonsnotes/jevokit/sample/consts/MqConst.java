package io.github.jevonsnotes.jevokit.sample.consts;

/**
 * MQ 演示用 topic/group 常量表：TOPIC_NORMAL/FIFO/DELAY/TRANSACTION 四类
 * 与两组消费者 group 的组合命名（值无品牌语义，与 broker 侧预先建好的 topic 对应）。
 */
public class MqConst {
    public static final String CONSUMER_GROUP_01 = "_CONSUMER_GROUP_01";
    public static final String CONSUMER_GROUP_02 = "_CONSUMER_GROUP_02";
    public static final String TOPIC_NORMAL = "TOPIC_NORMAL";
    public static final String TOPIC_NORMAL_CONSUMER_GROUP_01 = TOPIC_NORMAL + CONSUMER_GROUP_01;
    public static final String TOPIC_NORMAL_CONSUMER_GROUP_02 = TOPIC_NORMAL + CONSUMER_GROUP_02;
    public static final String TOPIC_FIFO = "TOPIC_FIFO";
    public static final String TOPIC_FIFO_CONSUMER_GROUP_01 = TOPIC_FIFO + CONSUMER_GROUP_01;
    public static final String TOPIC_FIFO_CONSUMER_GROUP_02 = TOPIC_FIFO + CONSUMER_GROUP_02;
    public static final String TOPIC_DELAY = "TOPIC_DELAY";
    public static final String TOPIC_DELAY_CONSUMER_GROUP_01 = TOPIC_DELAY + CONSUMER_GROUP_01;
    public static final String TOPIC_TRANSACTION = "TOPIC_TRANSACTION";
    public static final String TOPIC_TRANSACTION_CONSUMER_GROUP_01 = TOPIC_TRANSACTION + CONSUMER_GROUP_01;
}
