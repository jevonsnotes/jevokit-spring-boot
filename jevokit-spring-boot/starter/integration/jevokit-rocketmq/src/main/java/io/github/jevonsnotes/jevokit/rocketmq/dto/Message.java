package io.github.jevonsnotes.jevokit.rocketmq.dto;

import lombok.Data;
import org.springframework.messaging.MessageHeaders;

import java.util.Map;

/**
 * 不可变消息载体（payload + headers），异步链路中传递的消息统一形态；
 * 语义对齐 Spring Messaging 的 Message，供 RocketMqHelper 各发送入口复用。
 */
@Data
public class Message {

    private final String payload;

    private final Map<String,Object> headers;
}
