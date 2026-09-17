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
 * @Description TODO
 * @Date 2022/5/24 16:17
 * @Version 0.0.1
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
