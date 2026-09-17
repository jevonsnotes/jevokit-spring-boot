package io.github.jevonsnotes.jevokit.sample.api;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.UUID;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.github.jevonsnotes.jevokit.core.lang.CallbackP;
import io.github.jevonsnotes.jevokit.rocketmq.dto.MessageData;
import io.github.jevonsnotes.jevokit.rocketmq.helper.RocketMqHelper;
import io.github.jevonsnotes.jevokit.sample.consts.MqConst;
import io.github.jevonsnotes.jevokit.core.lang.ApiResponse;
import io.github.jevonsnotes.jevokit.sample.vo.TestVo;
import io.github.jevonsnotes.jevokit.sample.vo.TopicVo;

/**
 * POST localhost:8080/rocketmq/test
 * {
 * "type":1,
 * "async":true,
 * "transactionRollback":true,
 * "loop":1
 * }
 *
 */
@Slf4j
@RestController
@RequestMapping("/rocketmq")
@RequiredArgsConstructor
public class RocketMqApi {
    /**
     * 可选注入：RocketMqHelper 由 kit 装配条件化提供（@ConditionalOnBean(RocketMQTemplate)）。
     * 本机无 RocketMQ 环境时可用 local profile 排除 rocketmq 自动装配启动应用（见 resources/application-local.yml），
     * 此时 helper 不存在，仅在真正调用发送接口时给出明确报错，不影响应用启动。有 MQ 环境时行为与直接注入完全一致。
     */
    private final ObjectProvider<RocketMqHelper> rocketMqHelperProvider;

    /**
     * 获取 MQ 发送助手；未装配时抛出明确异常（仅在调用发送接口时触发，不在启动期阻塞）
     */
    private RocketMqHelper requireMqHelper() {
        RocketMqHelper helper = rocketMqHelperProvider.getIfAvailable();
        if (helper == null) {
            throw new IllegalStateException("RocketMQ 未装配（local profile 已排除 rocketmq 自动装配，或 starter 未生效），/rocketmq/** 发送接口不可用");
        }
        return helper;
    }

    @RequestMapping("/test")
/**
    * 四形态发送分发演示：type 1-4 对应普通/顺序/延迟/事务（helper 缺位时快速失败并说明原因）。
    * @param vo 消息体（type 决定形态）
    * @return 发送结果
    */
    public ApiResponse<String> test(@RequestBody TopicVo vo){
        switch (vo.getType()){
            case 1 :
                testTopicNormal(vo);
                break;
            case 2 :
                testTopicFIFO(vo);
                break;
            case 3 :
                testTopicDelay(vo);
                break;
            case 4 :
                testTopicTransaction(vo);
                break;
            default:
                break;
        }
        return ApiResponse.ok("success");
    }

    /**
     * sh mqadmin updateTopic -n <nameserver_address> -t <topic_name> -c <cluster_name> -a +message.type=NORMAL
     * @param vo
     */
    private void testTopicNormal(TopicVo vo){

        vo.setSendMsg("正常消息内容");
        final Message<MessageData<TopicVo>> data =
                MessageData.withData(vo).header("id", UUID.fastUUID().toString()).buildMsg();
//        looper(vo,(idx) -> {
//            if(vo.isAsync()){
//                rocketMqHelper.asyncSend(MqConst.TOPIC_NORMAL,msg);
//            } else {
//                rocketMqHelper.syncSend(MqConst.TOPIC_NORMAL,msg);
//            }
//        });
        log.info("发送普通消息："+ JSON.toJSONString(data));
        if(vo.isAsync()){
            requireMqHelper().asyncSend(MqConst.TOPIC_NORMAL+":TAG_A",data);
        } else {
            requireMqHelper().syncSend(MqConst.TOPIC_NORMAL+":TAG_A",data);
        }

    }
    private void testTopicFIFO(TopicVo vo){
        String messageGroup1 = "sameQueue1";
        String messageGroup2 = "sameQueue2";
        final String sendMsg = vo.getSendMsg();
//        looper(vo,(idx) -> rocketMqHelper.asyncSendOrderly(MqConst.TOPIC_FIFO,build,"sameQueue"));
        vo.setSendMsg(sendMsg + " 1");
        requireMqHelper().syncSendOrderly(MqConst.TOPIC_FIFO,vo,messageGroup1);
        vo.setSendMsg(sendMsg + " 2-1");
        requireMqHelper().syncSendOrderly(MqConst.TOPIC_FIFO,vo,messageGroup1);
        vo.setSendMsg(sendMsg + " 3");
        requireMqHelper().syncSendOrderly(MqConst.TOPIC_FIFO,vo,messageGroup1);
        vo.setSendMsg(sendMsg + " 2-2");
        requireMqHelper().syncSendOrderly(MqConst.TOPIC_FIFO,vo,messageGroup2);

    }
    private void testTopicDelay(TopicVo vo){
//        Message<TopicVo> msg = MessageBuilder
//                .withPayload(vo)
//                .build();
        final Message<MessageData<TopicVo>> data =
                MessageData.withData(vo).header("id", UUID.fastUUID().toString()).buildMsg();
//        looper(vo,(idx) -> rocketMqHelper.syncSendDelay(MqConst.TOPIC_DELAY,"延时消息内容："+ DateUtil.formatDate(DateUtil.date()),3000));
        requireMqHelper().syncSendDelay(MqConst.TOPIC_DELAY,data,1);
        requireMqHelper().syncSendDelay(MqConst.TOPIC_DELAY,data,2);
    }
    private void testTopicTransaction(TopicVo vo){
        final TestVo testVo = TestVo.builder().address("xxxx地址").mobile("123456487").build();
        final Message<TopicVo> msg = MessageBuilder
                .withPayload(vo)
                .setHeader("BusinessId", "B"+DateUtil.format(DateUtil.date(), DatePattern.PURE_DATETIME_PATTERN))
                .build();

        final TransactionSendResult transactionSendResult = requireMqHelper().syncSendTran(MqConst.TOPIC_TRANSACTION, msg, testVo);
        System.out.println("本地事务状态：" + transactionSendResult.getLocalTransactionState());
    }
    private void looper(TopicVo vo,CallbackP callback) {
        for(int i=0;i<vo.getLoop();i++){
            callback.call(i);
        }
    }
}
