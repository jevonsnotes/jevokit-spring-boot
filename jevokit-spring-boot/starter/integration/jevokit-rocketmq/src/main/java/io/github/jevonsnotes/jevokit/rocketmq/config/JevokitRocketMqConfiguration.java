package io.github.jevonsnotes.jevokit.rocketmq.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.jevonsnotes.jevokit.rocketmq.helper.RocketMqHelper;

/**
 * @Description TODO
 * @Date 2022/5/24 16:17
 * @Version 0.0.1
 */

@Configuration
// 包名迁移（旧 kit 单段包 → io.github.jevonsnotes.jevokit groupId 反演包）后 FQCN 字母序翻转到官方
// RocketMQAutoConfiguration（org.apache.*）之前，@ConditionalOnBean(RocketMQTemplate.class)
// 在模板 bean 注册前求值而静默不装配——显式声明次序锚定（与 audit-log 的 JdbcTemplate 同因）。
@AutoConfigureAfter(RocketMQAutoConfiguration.class)
@Slf4j
public class JevokitRocketMqConfiguration {
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
