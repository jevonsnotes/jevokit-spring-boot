package springbootkit.sample.consts;

/**
 * @Description TODO
 * @Date 2024/7/3 14:58
 * @Version 0.0.1
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
