package springbootkit.test.server.mq.listener.v4;//package springbootkit.test.server.mq.listener;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import springbootkit.test.server.consts.MqConst;
import springbootkit.test.server.vo.TopicVo;

/**
 * @Description TODO
 * @Date 2024/7/3 15:04
 * @Version 0.0.1
 */
@Slf4j
@Component
@RocketMQMessageListener(consumerGroup = MqConst.TOPIC_DELAY_CONSUMER_GROUP_01,
        topic = MqConst.TOPIC_DELAY,
        enableMsgTrace = true)
public class DelayMqListener implements RocketMQListener<TopicVo> {
    @Override
    public void onMessage(TopicVo message) {
        log.info("接收到延时消息：{}",JSON.toJSONString(message));
    }
}
