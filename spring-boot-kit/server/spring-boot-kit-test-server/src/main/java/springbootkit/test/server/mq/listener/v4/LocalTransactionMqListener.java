package springbootkit.test.server.mq.listener.v4;//package springbootkit.test.server.mq.listener;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * @Description 事务回查时间设置参考 https://rocketmq.apache.org/zh/docs/introduction/03limits
 * @Date 2024/7/3 15:04
 * @Version 0.0.1
 */
@Slf4j
@Component
@RocketMQTransactionListener
public class LocalTransactionMqListener implements RocketMQLocalTransactionListener {


    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        log.info("本地事务执行器收到消息：msg={} , arg = {}" ,JSON.toJSONString(msg),JSON.toJSONString(arg));
        return RocketMQLocalTransactionState.COMMIT;
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        log.info("事务回查监听器收到消息：msg={} " ,JSON.toJSONString(msg));

        return RocketMQLocalTransactionState.COMMIT;
    }
}
