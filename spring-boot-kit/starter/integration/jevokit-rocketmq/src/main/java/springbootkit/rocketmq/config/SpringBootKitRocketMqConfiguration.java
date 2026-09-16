package springbootkit.rocketmq.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springbootkit.rocketmq.helper.RocketMqHelper;

/**
 * @Description TODO
 * @Date 2022/5/24 16:17
 * @Version 0.0.1
 */

@Configuration
@Slf4j
public class SpringBootKitRocketMqConfiguration {
//    /**
//     * v5 配置
//     * @param rocketMQTemplate
//     * @return
//     */
//    @ConditionalOnBean(RocketMQClientTemplate.class)
//    @Bean
//    public RocketMqV5Helper rocketMqV5Helper(RocketMQClientTemplate rocketMQTemplate) {
//        return new RocketMqV5Helper(rocketMQTemplate);
//    }

    /**
     * v4配置
     * @param rocketMQTemplate
     * @return
     */
    @ConditionalOnBean(RocketMQTemplate.class)
    @Bean
    public RocketMqHelper rocketMqHelper(RocketMQTemplate rocketMQTemplate) {
        return new RocketMqHelper(rocketMQTemplate);
    }
}
