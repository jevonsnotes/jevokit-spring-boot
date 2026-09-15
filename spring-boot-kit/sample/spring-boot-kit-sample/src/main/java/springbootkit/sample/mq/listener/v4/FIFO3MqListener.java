package springbootkit.sample.mq.listener.v4;//package springbootkit.sample.mq.listener;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import springbootkit.sample.consts.MqConst;
import springbootkit.sample.vo.TopicVo;

@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = MqConst.TOPIC_FIFO_CONSUMER_GROUP_01,
        topic = MqConst.TOPIC_FIFO)
public class FIFO3MqListener implements RocketMQListener<TopicVo> {


    @Override
    public void onMessage(TopicVo message) {
        log.info("接收到【{}】消息={}", "03-顺序", JSON.toJSONString(message));
    }
}

