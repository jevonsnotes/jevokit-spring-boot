package io.github.jevonsnotes.jevokit.sample.mq.listener.v4;//package io.github.jevonsnotes.jevokit.sample.mq.listener;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import io.github.jevonsnotes.jevokit.sample.consts.MqConst;
import io.github.jevonsnotes.jevokit.sample.vo.TopicVo;

/**
 * 在业务场景中常用于另一个服务中监听事务消息
 */
@Slf4j
@Component
@RocketMQMessageListener(consumerGroup = MqConst.TOPIC_TRANSACTION_CONSUMER_GROUP_01,topic = MqConst.TOPIC_TRANSACTION)
public class TransactionMqListener implements RocketMQListener<TopicVo> {


    @Override
    public void onMessage(TopicVo message) {
        log.info("接收到事务消息：{}", JSON.toJSONString(message));
    }
}
