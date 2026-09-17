package io.github.jevonsnotes.jevokit.desensitization.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.jevonsnotes.jevokit.desensitization.api.DesensitizeApi;
import io.github.jevonsnotes.jevokit.desensitization.aspect.DesensitizationAspect;
import io.github.jevonsnotes.jevokit.desensitization.helper.DesHelper;
import io.github.jevonsnotes.jevokit.desensitization.interceptor.SensitizationPowerInterceptor;
import io.github.jevonsnotes.jevokit.desensitization.interceptor.impl.DefaultSensitizationPowerInterceptor;

/**
 * 脱敏模块装配类：注册切面（@Desensitization 入口）、脱敏辅助器、权限拦截器、
 * 配置属性与解密 API 控制器。
 * 使用方式：引入本 starter 即生效；切面/DesHelper/权限拦截器均以 @ConditionalOnMissingBean 注册，
 * 业务侧注册同类型 bean 即可替换默认实现。
 * <p>关键约束：{@link JevokitDesensitizationProperties} 带 @RefreshScope，规则可经配置中心热更新；
 * 默认权限拦截器 {@link DefaultSensitizationPowerInterceptor} 恒拒绝（任何人无明文权限），
 * 需要"有权限角色看明文"能力时必须自行实现 {@link SensitizationPowerInterceptor}。
 */

@Configuration
@Slf4j
public class JevokitDesensitizationConfiguration{

    @ConditionalOnMissingBean
    @Bean
    public DesensitizationAspect desensitizationAspect(){
        return new DesensitizationAspect();
    }

    @ConditionalOnMissingBean
    @Bean
    public DesHelper desHelper(JevokitDesensitizationProperties properties,SensitizationPowerInterceptor sensitizationPowerInterceptor){
        return new DesHelper(properties,sensitizationPowerInterceptor);
    }


    @RefreshScope
    @Bean
    public JevokitDesensitizationProperties jevokitDesensitizationProperties(){
        return new JevokitDesensitizationProperties();
    }
    /**
     * 权限拦截器，允许通过实现类控制部分拥有权限的角色可以忽略脱敏
     * @return
     */
    @ConditionalOnMissingBean
    @Bean
    public SensitizationPowerInterceptor sensitizationPowerInterceptor(){
        return new DefaultSensitizationPowerInterceptor();
    }
    @Bean
    public DesensitizeApi desensitizeApi(){
        return new DesensitizeApi();
    }
//    @Bean
//    public DesensitizationApiInterceptor desensitizationApiInterceptor(JevokitDesensitizationProperties properties){
//        return new DesensitizationApiInterceptor(properties);
//    }
//    @Bean
//    public JevokitDesensitizationWebConfiguration jevokitDesensitizationWebConfiguration(JevokitDesensitizationProperties properties){
//        return new JevokitDesensitizationWebConfiguration(properties);
//    }
}
