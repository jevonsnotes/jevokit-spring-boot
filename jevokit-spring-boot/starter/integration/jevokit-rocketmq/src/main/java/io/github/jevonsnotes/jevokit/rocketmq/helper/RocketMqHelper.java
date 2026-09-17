package io.github.jevonsnotes.jevokit.rocketmq.helper;

import cn.hutool.core.thread.ExecutorBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
 * @Description TODO
 * @Date 2024/7/3 14:40
 * @Version 0.0.1
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



    @PostConstruct
    public void init() {
        log.info("RocketMqHelper 初始化");
    }

    /**
     * 发送异步消息
     *
     * @param topic   消息Topic
     * @param message 消息实体
     */
    public void asyncSend(Enum topic, Object message) {
        asyncSend(topic.name(), message, getDefaultSendCallBack());
    }

    /**
     * 发送异步消息
     *
     * @param topic   消息Topic
     * @param message 消息实体
     */
    public void asyncSend(Enum topic, Message message) {
        asyncSend(topic.name(), message, getDefaultSendCallBack());
    }

    /**
     * 发送异步消息
     *
     * @param topic   消息Topic
     * @param message 消息实体
     */
    public void asyncSend(String topic, Object message) {
        asyncSend(topic, convertMessage(message));
    }
    /**
     * 发送异步消息
     *
     * @param topic   消息Topic
     * @param message 消息实体
     */
    public void asyncSend(String topic, Message message) {
        asyncSend(topic, message, getDefaultSendCallBack());
    }

    /**
     * 发送异步消息
     * @param topic
     * @param message
     * @param messageGroup 确保同一个消息组的消息按顺序存储
     */
    public void asyncSendOrderly(String topic, Object message,String messageGroup) {
        asyncSend(topic, convertMessage(message),messageGroup,null,null,TopicMsgType.FIFO,getDefaultSendCallBack());
    }


    /**
     * 发送异步消息
     *
     * @param topic        消息Topic
     * @param message      消息实体
     * @param sendCallback 回调函数
     */
    public void asyncSend(Enum topic, Object message, SendCallback sendCallback) {
        asyncSend(topic.name(), message, sendCallback);
    }
    /**
     * 发送异步消息
     *
     * @param topic        消息Topic
     * @param message      消息实体
     * @param sendCallback 回调函数
     */
    public void asyncSend(Enum topic, Message<?> message, SendCallback sendCallback) {
        asyncSend(topic.name(), message, sendCallback);
    }

    /**
     * 发送异步消息
     * DelayLevel 按序号对应 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     * @param topic   消息Topic
     * @param message 消息实体
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
     * 发送异步消息
     *
     * @param topic        消息Topic
     * @param message      消息实体
     * @param sendCallback 回调函数
     */
    public void asyncSend(String topic, Object message, SendCallback sendCallback) {
        asyncSend(topic, convertMessage(message), sendCallback);
    }
    /**
     * 发送异步消息
     *
     * @param topic        消息Topic
     * @param message      消息实体
     * @param sendCallback 回调函数
     */
    public void asyncSend(String topic, Message<?> message, SendCallback sendCallback) {
        asyncSend(topic, message, null,null,null,TopicMsgType.NORMAL,sendCallback);
    }

    private Message convertMessage(Object message){
        if(message instanceof Message){
            return (Message) message;
        } else {
            return MessageBuilder.withPayload(message).build();
        }
    }

    /**
     * 发送异步消息
     *
     * @param topic        消息Topic
     * @param message      消息实体
     * @param sendCallback 回调函数
     * @param delayLevel    延时时间
     */
    public void asyncSend(String topic, Message<?> message, SendCallback sendCallback, Long timeout,int delayLevel) {
        asyncSend(topic, message, null,timeout,delayLevel,TopicMsgType.DELAY,sendCallback);
    }


    /**
     * 发送顺序消息
     *
     * @param message
     * @param topic
     * @param messageGroup 相同hashKey会发送到同一个队列
     */
    public void syncSendOrderly(Enum topic, Message message, String messageGroup) {
        syncSendOrderly(topic.name(), message, messageGroup);
    }


    /**
     * 发送顺序消息
     *
     * @param message
     * @param topic
     * @param messageGroup
     */
    public void syncSendOrderly(String topic, Message message, String messageGroup) {
        rocketMQTemplate.syncSendOrderly(topic, message, messageGroup);
    }
    /**
     * 发送顺序消息
     *
     * @param message
     * @param topic
     * @param messageGroup
     */
    public void syncSendOrderly(String topic, Object message, String messageGroup) {
        rocketMQTemplate.syncSendOrderly(topic, convertMessage(message), messageGroup);
    }
    /**
     * 发送普通消息
     * @param message
     * @param topic
     */
    public void syncSend(String topic, Object message) {
        syncSend(topic,convertMessage(message));
    }

    /**
     * 发送普通消息
     * @param message
     * @param topic
     */
    public void syncSend(String topic, Message<?> message) {
        rocketMQTemplate.syncSend(topic, message);
    }


    /**
     *  发送延时消息
     * @param topic
     * @param message
     * @param delayLevel MILLISECONDS
     */
    public void syncSendDelay(String topic, Object message, int delayLevel) {
        syncSendDelay(topic,convertMessage(message),null,delayLevel);
    }
    /**
     *  发送延时消息
     * @param topic
     * @param message
     * @param delayLevel 按序号对应 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     */
    public void syncSendDelay(String topic, Message<?> message, Long timeout, int delayLevel) {
        timeout = timeout == null ? rocketMQTemplate.getProducer().getSendMsgTimeout():timeout;
        rocketMQTemplate.syncSend(topic, message,timeout,delayLevel);
    }


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
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("发送消息成功：{}",sendResult.getMsgId());
            }

            @Override
            public void onException(Throwable e) {
                log.info("发送消息失败：{}",e.getMessage());
            }
        };
    }


    @PreDestroy
    public void destroy() {
        log.info("RocketMqHelper 注销");
    }

}
