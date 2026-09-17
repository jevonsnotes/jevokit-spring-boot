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


    /** 注册模块配置（@RefreshScope 支持配置中心热更新终端/令牌规则）。 */
    @RefreshScope
    @Bean
    public JevokitApiProperties jevokitApiProperties(){
        return new JevokitApiProperties();
    }
    /** 注册 MVC 装配器（挂载 token 准入拦截器）。 */
    @Bean
    public JevokitApiWebMvcConfigurer jevokitApiWebMvcConfigurer(JevokitApiProperties properties){
        return new JevokitApiWebMvcConfigurer(properties);
    }

    /** 注册请求体解密适配器（协议入站链路）。 */
    @Bean
    public DecryptRequestAdviceAdapter decryptRequestAdviceAdapter(JevokitApiProperties properties,IPreventReplayAttack iPreventReplayAttack){
        return new DecryptRequestAdviceAdapter(properties,iPreventReplayAttack);
    }

    /** 注册响应体加密适配器（协议出站链路）。 */
    @Bean
    public EncryptResponseAdviceAdapter encryptResponseAdviceAdapter(JevokitApiProperties properties){
        return new EncryptResponseAdviceAdapter(properties);
    }
    /** 注册协议工具（注入配置以启用静态方法）。 */
    @Bean
    public JSecureApiUtil jSecureApiUtil(JevokitApiProperties properties){
        return new JSecureApiUtil(properties);
    }
    /** 注册默认防重放实现（进程内存储；业务自定义 bean 优先，多实例部署应替换）。 */
    @ConditionalOnMissingBean(IPreventReplayAttack.class)
    @Bean
    public IPreventReplayAttack iPreventReplayAttack(){
        return new DefaultPreventReplayAttack();
    }

}
