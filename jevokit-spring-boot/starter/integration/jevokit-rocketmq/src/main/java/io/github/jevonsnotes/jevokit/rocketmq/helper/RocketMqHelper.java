package io.github.jevonsnotes.jevokit.rocketmq.helper;

import cn.hutool.core.thread.ExecutorBuilder;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import io.github.jevonsnotes.jevokit.rocketmq.consts.TopicMsgType;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * RocketMQ 收发门面：在 RocketMQTemplate 之上封装同步/异步/顺序/延迟四类常用发送形态，
 * topic 可用 Enum（取 name()）或字符串；异步走内部线程池（容量 10000，CallerRunsPolicy 兜底，
 * 队列满时退化为发送线程自执行，不丢消息但会阻塞调用方）。
 *
 * <p>用法：由 starter 在 RocketMQTemplate Bean 在位时自动装配（@ConditionalOnBean 守卫，
 * 无 MQ 环境不装配）；消息体 Object 自动包装为 MessageData，headers 随消息透传。
 *
 * <p>关键约束：默认发送超时 5s（SEND_TIMEOUT）；延迟消息用 delayLevel（RocketMQ 4.x 的
 * 18 级延迟档位，非任意时刻）；顺序消息依赖 messageGroup 哈希到同一队列。
 */
@Slf4j
@RequiredArgsConstructor
public class RocketMqHelper {

    /**
     * rocketmq模板注入
     */
    private final RocketMQTemplate rocketMQTemplate;
    private static final int factor = 1+300/20;//1+目标IO耗时/目标CPU耗时
    private static final int cpuCore = Runtime.getRuntime().availableProcessors();//核心数
    private static final int corePoolSize = cpuCore*factor;
    private static final int maxPoolSize = corePoolSize*2;
    private static final long keepAliveTime = TimeUnit.SECONDS.toNanos(30L);
    private static final int CAPACITY = 10000;
    private static final long SEND_TIMEOUT = 5000;

    private ExecutorService pool = ExecutorBuilder
            .create()
            .setHandler(new ThreadPoolExecutor.CallerRunsPolicy())//超过队列则自己执行策略
            .setCorePoolSize(corePoolSize)
            .setMaxPoolSize(maxPoolSize)
            .setAllowCoreThreadTimeOut(true)
            .setKeepAliveTime(keepAliveTime)
            .setWorkQueue(new LinkedBlockingQueue<>(CAPACITY)).build();



    /**
    * 生命周期日志（@PostConstruct）：装配完成即打点，无其他副作用。
    */
    @PostConstruct
    public void init() {
        log.info("RocketMqHelper 初始化");
    }

    /**
    * 异步发送（枚举 topic 取 name()），回调用默认实现（成败各打一条日志）。
    * @param topic 目标 Topic（枚举名即 topic 名）
    * @param message 业务消息，自动包装为 Spring Messaging Message
    */
    public void asyncSend(Enum topic, Object message) {
        asyncSend(topic.name(), message, getDefaultSendCallBack());
    }

    /**
    * 异步发送（枚举 topic + 已包装消息），默认回调。
    * @param topic 目标 Topic
    * @param message 已包装消息
    */
    public void asyncSend(Enum topic, Message message) {
        asyncSend(topic.name(), message, getDefaultSendCallBack());
    }

    /**
    * 异步发送（字符串 topic），默认回调。语义同 {@link #asyncSend(String, Message, SendCallback)}。
    * @param topic 目标 Topic（destination 语法，支持 topic:tag）
    * @param message 业务消息
    */
    public void asyncSend(String topic, Object message) {
        asyncSend(topic, convertMessage(message));
    }
    /**
    * 异步发送（字符串 topic + 已包装消息），默认回调。
    * @param topic 目标 Topic
    * @param message 已包装消息
    */
    public void asyncSend(String topic, Message message) {
        asyncSend(topic, message, getDefaultSendCallBack());
    }

    /**
    * 异步顺序发送：相同 messageGroup 哈希到同一队列，组内保序。
    * @param topic 目标 Topic
    * @param message 业务消息
    * @param messageGroup 分区键——需保序的业务维度（如同一单号）
    */
    public void asyncSendOrderly(String topic, Object message,String messageGroup) {
        asyncSend(topic, convertMessage(message),messageGroup,null,null,TopicMsgType.FIFO,getDefaultSendCallBack());
    }


    /**
    * 异步发送（枚举 topic + 自定义回调）。
    * @param topic 目标 Topic
    * @param message 业务消息
    * @param sendCallback 成败回调（补偿/重投挂载点），null 同默认回调
    */
    public void asyncSend(Enum topic, Object message, SendCallback sendCallback) {
        asyncSend(topic.name(), message, sendCallback);
    }
    /**
    * 异步发送（枚举 topic + 已包装消息 + 自定义回调）。
    * @param topic 目标 Topic
    * @param message 已包装消息
    * @param sendCallback 成败回调
    */
    public void asyncSend(Enum topic, Message<?> message, SendCallback sendCallback) {
        asyncSend(topic.name(), message, sendCallback);
    }

    /**
    * 异步发送全参形态：按 topicMsgType 路由 NORMAL/DELAY/FIFO 三分支。
    * @param topic 目标 Topic
    * @param message 已包装消息
    * @param messageGroup FIFO 分区键（仅 FIFO 分支使用）
    * @param timeout 超时毫秒，null 取默认 5000
    * @param delayLevel 延迟档位序号（1-18 对应 1s…2h），null 时取 producer sendMsgTimeout
    * @param topicMsgType 发送形态（见 TopicMsgType）
    * @param sendCallback 原生回调；null 仅在 NORMAL 分支回退默认回调，DELAY/FIFO 分支原样透传 null
    * （引擎侧收到 null 回调的行为未定义——自定义形态请显式传回调）
    */
    public void asyncSend(String topic, Message<?> message,String messageGroup,Long timeout, Integer delayLevel,TopicMsgType topicMsgType, org.apache.rocketmq.client.producer.SendCallback sendCallback) {
        timeout = timeout == null ? SEND_TIMEOUT :timeout;
        delayLevel = delayLevel == null ? rocketMQTemplate.getProducer().getSendMsgTimeout() : delayLevel;
        SendCallback callback = sendCallback == null ? getDefaultSendCallBack() : sendCallback;

        switch (topicMsgType){
            case DELAY:
                rocketMQTemplate.asyncSend(topic,message,sendCallback,timeout,delayLevel);
                break;
            case FIFO:
                rocketMQTemplate.asyncSendOrderly(topic,message, messageGroup, sendCallback,timeout);
                break;
            default:
                rocketMQTemplate.asyncSend(topic, message, callback);
                break;
        }

    }

    /**
    * 异步发送（字符串 topic + 业务消息 + 自定义回调）。
    * @param topic 目标 Topic
    * @param message 业务消息
    * @param sendCallback 成败回调
    */
    public void asyncSend(String topic, Object message, SendCallback sendCallback) {
        asyncSend(topic, convertMessage(message), sendCallback);
    }
    /**
    * 异步发送便捷形态：等价全参形态的 NORMAL + 默认超时。
    * @param topic 目标 Topic
    * @param message 已包装消息
    * @param sendCallback 成败回调
    */
    public void asyncSend(String topic, Message<?> message, SendCallback sendCallback) {
        asyncSend(topic, message, null,null,null,TopicMsgType.NORMAL,sendCallback);
    }

    /**
    * 已是 Message 直接返回，否则以业务对象为 payload 包装。
    */
    private Message convertMessage(Object message){
        if(message instanceof Message){
            return (Message) message;
        } else {
            return MessageBuilder.withPayload(message).build();
        }
    }

    /**
    * 异步延迟发送便捷形态：固定走 DELAY 分支。
    * @param topic 目标 Topic
    * @param message 已包装消息
    * @param sendCallback 成败回调（注意全参形态对 null 回调的透传语义）
    * @param timeout 超时毫秒，null 取默认
    * @param delayLevel 延迟档位序号（1-18）
    */
    public void asyncSend(String topic, Message<?> message, SendCallback sendCallback, Long timeout,int delayLevel) {
        asyncSend(topic, message, null,timeout,delayLevel,TopicMsgType.DELAY,sendCallback);
    }


    /**
    * 同步顺序发送（枚举 topic）。语义同 {@link #syncSendOrderly(String, Message, String)}。
    * @param topic 目标 Topic
    * @param message 已包装消息
    * @param messageGroup 分区键，相同键进同一队列保序
    */
    public void syncSendOrderly(Enum topic, Message message, String messageGroup) {
        syncSendOrderly(topic.name(), message, messageGroup);
    }


    /**
    * 同步顺序发送：阻塞至 broker 确认。
    * @param topic 目标 Topic
    * @param message 已包装消息
    * @param messageGroup 分区键
    */
    public void syncSendOrderly(String topic, Message message, String messageGroup) {
        rocketMQTemplate.syncSendOrderly(topic, message, messageGroup);
    }
    /**
    * 同步顺序发送（业务消息自动包装）。
    * @param topic 目标 Topic
    * @param message 业务消息
    * @param messageGroup 分区键
    */
    public void syncSendOrderly(String topic, Object message, String messageGroup) {
        rocketMQTemplate.syncSendOrderly(topic, convertMessage(message), messageGroup);
    }
    /**
    * 同步普通发送（业务消息自动包装）：阻塞至 broker 确认，失败抛异常。
    * @param topic 目标 Topic
    * @param message 业务消息
    */
    public void syncSend(String topic, Object message) {
        syncSend(topic,convertMessage(message));
    }

    /**
    * 同步普通发送（已包装消息）。
    * @param topic 目标 Topic
    * @param message 已包装消息
    */
    public void syncSend(String topic, Message<?> message) {
        rocketMQTemplate.syncSend(topic, message);
    }


    /**
    * 同步延迟发送（业务消息自动包装，默认超时）。
    * @param topic 目标 Topic
    * @param message 业务消息
    * @param delayLevel 延迟档位序号（1-18 对应 1s…2h，非任意时刻）
    */
    public void syncSendDelay(String topic, Object message, int delayLevel) {
        syncSendDelay(topic,convertMessage(message),null,delayLevel);
    }
    /**
    * 同步延迟发送全参形态：超时 null 时取 producer sendMsgTimeout。
    * @param topic 目标 Topic
    * @param message 已包装消息
    * @param timeout 超时毫秒，null 取 producer 配置
    * @param delayLevel 延迟档位序号（1-18）
    */
    public void syncSendDelay(String topic, Message<?> message, Long timeout, int delayLevel) {
        timeout = timeout == null ? rocketMQTemplate.getProducer().getSendMsgTimeout():timeout;
        rocketMQTemplate.syncSend(topic, message,timeout,delayLevel);
    }


    /**
    * 同步事务消息：半消息发出后由本地事务执行器的执行结果驱动提交/回滚。
    * @param topic 目标 Topic
    * @param message 已包装消息
    * @param arg 透传给本地事务执行/回查的上下文参数
    * @return 引擎侧事务发送结果（含本地事务状态与消息 id）
    */
    public TransactionSendResult syncSendTran(String topic, Message message,  Object arg) {
        return rocketMQTemplate.sendMessageInTransaction(topic, message, arg);
    }

    /**
     * 默认CallBack函数
     *
     * @return
     */
    private SendCallback getDefaultSendCallBack() {
        return new SendCallback() {
            /**
            * 匿名回调覆写：成功打 msgId 日志。
            */
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("发送消息成功：{}",sendResult.getMsgId());
            }

            /**
            * 匿名回调覆写：失败打异常消息日志。
            */
            @Override
            public void onException(Throwable e) {
                log.info("发送消息失败：{}",e.getMessage());
            }
        };
    }


    @PreDestroy
    /**
    * 生命周期日志（@PreDestroy）：销毁打点，无其他清理（客户端由官方 starter 托管）。
    */
    public void destroy() {
        log.info("RocketMqHelper 注销");
    }

}
