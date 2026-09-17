package io.github.jevonsnotes.jevokit.rocketmq.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import io.github.jevonsnotes.jevokit.core.util.CollectionUtil;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 业务消息包装器：data 为业务负载，headers 链式累积后经 buildMsg 转为 Spring Messaging
 * Message 交 RocketMQTemplate 发送。ID/TIMESTAMP 两个只读头透传时跳过（引擎自管，见
 * isReadOnly——校验逻辑 copy 自 MessageHeaderAccessor.setHeader，行为须与上游保持一致）。
 *
 * <p>用法：MessageData.withData(obj).header(k, v)...buildMsg()。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageData<T> {
    private T data;
    private Map<String,Object> headers;

/**
    * 工厂入口：以业务负载构造（headers 初始化为空表）。
    * @param data 业务负载
    * @return 新包装实例
    */
    public static <T>MessageData<T> withData(T data){
        return new MessageData(data, new HashMap<>());
    }
/**
    * 整体替换 headers（空 Map 忽略不替换）。
    * @param headers 头集合
    * @return this（链式）
    */
    public MessageData<T> headers(Map<String,Object> headers){
        this.headers = CollectionUtil.isEmpty(headers) ? this.headers : headers;
        return this;
    }

/**
    * 追加单个头。
    * @param key 头名
    * @param value 头值
    * @return this（链式）
    */
    public MessageData<T> header(String key,Object value){
        this.headers .put(key,value);
        return this;
    }


/**
    * 转为 Spring Messaging Message（ID/TIMESTAMP 只读头跳过，见类注释）。
    * @return 可发送的消息
    */
    public Message<MessageData<T>> buildMsg(){

        final MessageBuilder<MessageData<T>> messageDataMessageBuilder = MessageBuilder.withPayload(this);
        final Iterator<Map.Entry<String, Object>> iterator = headers.entrySet().iterator();
        while(iterator.hasNext()){
            final Map.Entry<String, Object> next = iterator.next();
            if(!isReadOnly(next.getKey())){
                messageDataMessageBuilder.setHeader(next.getKey(),next.getValue());
            }
        }
        return messageDataMessageBuilder.build();

    }

    /**
     * copy from MessageHeaderAccessor.setHeader validation
     * @param headerName
     * @return
     */
    private boolean isReadOnly(String headerName) {
        return (MessageHeaders.ID.equals(headerName) || MessageHeaders.TIMESTAMP.equals(headerName));
    }
}
