package io.github.jevonsnotes.jevokit.sample.mq.listener.v4;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.lang.generator.SnowflakeGenerator;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import io.github.jevonsnotes.jevokit.redis.cache.generator.extend.rocketmq.RocketMqV5KeyGenerator;
import io.github.jevonsnotes.jevokit.redis.lock.annotation.Key;
import io.github.jevonsnotes.jevokit.redis.lock.annotation.Lock;
import io.github.jevonsnotes.jevokit.rocketmq.dto.MessageData;
import io.github.jevonsnotes.jevokit.sample.consts.MqConst;
import io.github.jevonsnotes.jevokit.sample.vo.TopicVo;

/**
 * 普通消息消费示例：消费 MessageData&lt;TopicVo&gt;，方法级 @Lock(route=data.id) 演示
 * "同业务键禁止并发消费"——锁冲突时按 fallbackWithException 抛出转重试。
 */
@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = MqConst.TOPIC_NORMAL_CONSUMER_GROUP_01,
        topic = MqConst.TOPIC_NORMAL,
        enableMsgTrace = true,
        maxReconsumeTimes = 1,
        consumeTimeout = 1)
public class NormalMqListener implements RocketMQListener<MessageData<TopicVo>> {

    @Lock(namespace = "TOPIC_CONCURRENT_LOCK", key = @Key(route = "data.id"),fallbackWithException = RuntimeException.class, fallbackStr = "禁止并发消费")
    @Override
    public void onMessage(MessageData<TopicVo> message) {

        log.info("接收到消息：{}，开始sleep = {}ms",JSON.toJSONString(message),message.getData().getSleepTime());
        ThreadUtil.sleep(message.getData().getSleepTime());
        log.info("sleep 结束");
        if(message.getData().isTransactionRollback()){
            throw  new RuntimeException("测试消费失败现象");
        }
        SpringUtil.getBean(NormalMqListener.class).doLocalBusiness(message.getData());
    }

    @Cacheable(cacheNames = "consume_result_cache#expire=1800",unless = "!#result")
    public boolean doLocalBusiness(TopicVo message){
        return !message.isTransactionRollback();
    }
}
