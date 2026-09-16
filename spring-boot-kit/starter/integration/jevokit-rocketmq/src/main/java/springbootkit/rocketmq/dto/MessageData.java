package springbootkit.rocketmq.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import springbootkit.core.util.CollectionUtil;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @Description TODO
 * @Date 2024/7/12 14:22
 * @Version 0.0.1
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageData<T> {
    private T data;
    private Map<String,Object> headers;

    public static <T>MessageData<T> withData(T data){
        return new MessageData(data, new HashMap<>());
    }
    public MessageData<T> headers(Map<String,Object> headers){
        this.headers = CollectionUtil.isEmpty(headers) ? this.headers : headers;
        return this;
    }

    public MessageData<T> header(String key,Object value){
        this.headers .put(key,value);
        return this;
    }


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
