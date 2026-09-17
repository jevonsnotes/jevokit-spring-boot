package io.github.jevonsnotes.jevokit.sample.mq.listener.v4;//package io.github.jevonsnotes.jevokit.sample.mq.listener;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

 /**
 * 事务回查时间设置参考 https://rocketmq.apache.org/zh/docs/introduction/03limits
 */
@Slf4j
@Component
@RocketMQTransactionListener
public class LocalTransactionMqListener implements RocketMQLocalTransactionListener {


    @Override
    /**
    * 事务消息本地事务执行演示：返回 COMMIT 驱动半消息投递。
    * @param msg 半消息
    * @param arg 发起时透传上下文
    * @return 本地事务状态（演示恒 COMMIT）
    */
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        log.info("本地事务执行器收到消息：msg={} , arg = {}" ,JSON.toJSONString(msg),JSON.toJSONString(arg));
        return RocketMQLocalTransactionState.COMMIT;
    }

    @Override
    /**
    * 事务回查演示：长时间未收到本地事务结果时引擎回调。
    * @param msg 半消息
    * @return 回查结论状态
    */
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        log.info("事务回查监听器收到消息：msg={} " ,JSON.toJSONString(msg));

        return RocketMQLocalTransactionState.COMMIT;
    }
}
