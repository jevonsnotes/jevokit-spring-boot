package springbootkit.rocketmq.dto;

import lombok.Data;
import org.springframework.messaging.MessageHeaders;

import java.util.Map;

/**
 * @Description TODO
 * @Date 2024/7/5 17:40
 * @Version 0.0.1
 */
@Data
public class Message {

    private final String payload;

    private final Map<String,Object> headers;
}
