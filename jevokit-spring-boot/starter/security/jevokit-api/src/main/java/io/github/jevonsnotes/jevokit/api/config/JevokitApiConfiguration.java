package io.github.jevonsnotes.jevokit.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.jevonsnotes.jevokit.api.advice.DecryptRequestAdviceAdapter;
import io.github.jevonsnotes.jevokit.api.advice.EncryptResponseAdviceAdapter;
import io.github.jevonsnotes.jevokit.api.service.IPreventReplayAttack;
import io.github.jevonsnotes.jevokit.api.service.impl.DefaultPreventReplayAttack;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;

/**
 * @Description TODO
 * @Date 2022/5/24 16:17
 * @Version 0.0.1
 */
@Configuration
@Slf4j
public class JevokitApiConfiguration {


    @RefreshScope
    @Bean
    public JevokitApiProperties jevokitApiProperties(){
        return new JevokitApiProperties();
    }
    @Bean
    public JevokitApiWebMvcConfigurer jevokitApiWebMvcConfigurer(JevokitApiProperties properties){
        return new JevokitApiWebMvcConfigurer(properties);
    }

    @Bean
    public DecryptRequestAdviceAdapter decryptRequestAdviceAdapter(JevokitApiProperties properties,IPreventReplayAttack iPreventReplayAttack){
        return new DecryptRequestAdviceAdapter(properties,iPreventReplayAttack);
    }

    @Bean
    public EncryptResponseAdviceAdapter encryptResponseAdviceAdapter(JevokitApiProperties properties){
        return new EncryptResponseAdviceAdapter(properties);
    }
    @Bean
    public JSecureApiUtil jSecureApiUtil(JevokitApiProperties properties){
        return new JSecureApiUtil(properties);
    }
    @ConditionalOnMissingBean(IPreventReplayAttack.class)
    @Bean
    public IPreventReplayAttack iPreventReplayAttack(){
        return new DefaultPreventReplayAttack();
    }

}
