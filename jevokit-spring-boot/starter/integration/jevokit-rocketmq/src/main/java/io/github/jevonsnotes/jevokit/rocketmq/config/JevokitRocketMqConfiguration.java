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
 * rocketmq 自动装配：官方 RocketMQTemplate 在位时装配 RocketMqHelper 门面 Bean。
 *
 * <p>关键约束：@ConditionalOnBean(RocketMQTemplate.class) + @AutoConfigureAfter
 * （RocketMQAutoConfiguration）双重守卫——次序锚定防 FQCN 字母序漂移导致条件先于模板
 * 注册而静默不装配（教训 bug-package-rename-fqcn-sort-flip-20260917）；无 MQ 环境
 * （如本地 local profile 排除官方装配）本配置整体不生效。
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
/**
    * 装配 RocketMqHelper 门面。
    * @param rocketMQTemplate 官方模板（@ConditionalOnBean 守卫的前提 Bean）
    * @return 发送门面单例
    */
    public RocketMqHelper rocketMqHelper(RocketMQTemplate rocketMQTemplate) {
        return new RocketMqHelper(rocketMQTemplate);
    }
}
