package io.github.jevonsnotes.jevokit.sample.mq.listener.v4;//package io.github.jevonsnotes.jevokit.sample.mq.listener;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import io.github.jevonsnotes.jevokit.sample.consts.MqConst;
import io.github.jevonsnotes.jevokit.sample.vo.TopicVo;

 /**
 * 延迟消息消费示例：RocketMQ 4.x delayLevel 档位消息的消费端形态（裸 TopicVo，
 * 无锁演示），与 syncSendDelay 配对。
 */
@Slf4j
@Component
@RocketMQMessageListener(consumerGroup = MqConst.TOPIC_DELAY_CONSUMER_GROUP_01,
        topic = MqConst.TOPIC_DELAY,
        enableMsgTrace = true)
public class DelayMqListener implements RocketMQListener<TopicVo> {
    @Override
    /**
    * 延迟消息消费演示（裸 TopicVo）。
    * @param message 消息体
    */
    public void onMessage(TopicVo message) {
        log.info("接收到延时消息：{}",JSON.toJSONString(message));
    }
}
