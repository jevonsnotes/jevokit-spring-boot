package io.github.jevonsnotes.jevokit.sample.mq.listener.v4;//package io.github.jevonsnotes.jevokit.sample.mq.listener;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import io.github.jevonsnotes.jevokit.redis.lock.annotation.Key;
import io.github.jevonsnotes.jevokit.redis.lock.annotation.Lock;
import io.github.jevonsnotes.jevokit.sample.consts.MqConst;
import io.github.jevonsnotes.jevokit.sample.vo.TopicVo;

@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = MqConst.TOPIC_FIFO_CONSUMER_GROUP_01,
        topic = MqConst.TOPIC_FIFO)
public class FIFO2MqListener implements RocketMQListener<TopicVo> {


    @Lock(namespace = "TOPIC_CONCURRENT_LOCK", fallbackWithException = RuntimeException.class, fallbackStr = "禁止并发消费", key = @Key(route = "messageId"))
    @Override
    public void onMessage(TopicVo message) {
        log.info("接收到【{}】消息={} ", "02-顺序", JSON.toJSONString(message));
    }
}

