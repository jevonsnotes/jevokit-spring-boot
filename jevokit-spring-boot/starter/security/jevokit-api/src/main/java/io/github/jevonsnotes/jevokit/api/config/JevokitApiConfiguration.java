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
 * JSecure API 模块装配类：注册协议全链路组件——请求体解密/响应体加密适配器、访问拦截器、
 * 终端防重放服务与 {@link JSecureApiUtil} 工具。
 * 使用方式：引入本 starter 即生效；接入方在 Controller 方法标注 {@code @JSecureApi} 并以
 * {@code JSecureRequest<T>} 收参、{@code JSecureResponse<T>} 返回即完成协议接入。
 * <p>关键约束：防重放默认实现 {@link DefaultPreventReplayAttack} 以 @ConditionalOnMissingBean 注册，
 * 业务侧自定义 {@link IPreventReplayAttack} bean 即可整体替换（如改为 Redis 集中存储）。
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
