package io.github.jevonsnotes.jevokit.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import io.github.jevonsnotes.jevokit.api.advice.DecryptRequestAdviceAdapter;
import io.github.jevonsnotes.jevokit.api.advice.EncryptResponseAdviceAdapter;
import io.github.jevonsnotes.jevokit.api.service.IPreventReplayAttack;
import io.github.jevonsnotes.jevokit.api.service.impl.DefaultPreventReplayAttack;
import io.github.jevonsnotes.jevokit.api.upload.JSecureFileFetcher;
import io.github.jevonsnotes.jevokit.api.upload.JSecureUploadFileRefChecker;
import io.github.jevonsnotes.jevokit.api.upload.JSecureUploadGuard;
import io.github.jevonsnotes.jevokit.api.upload.JSecureUploadVerifier;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;

/**
 * JSecure API 模块装配类：注册协议全链路组件——请求体解密/响应体加密适配器、访问拦截器、
 * 终端防重放服务、上传校验/拉取组件与 {@link JSecureApiUtil} 工具。
 * 使用方式：引入本 starter 即生效；接入方在 Controller 方法标注 {@code @JSecureApi} 并以
 * {@code JSecureRequest<T>} 收参、{@code JSecureResponse<T>} 返回即完成协议接入
 * （文件上传用 meta+files 双参数形态，见 README 上传章节）。
 * <p>关键约束：防重放默认实现 {@link DefaultPreventReplayAttack} 以 @ConditionalOnMissingBean 注册，
 * 业务侧自定义 {@link IPreventReplayAttack} bean 即可整体替换（如改为 Redis 集中存储）；
 * refer 模式存在性钩子 {@link JSecureUploadFileRefChecker} 由业务按需注册，无实现时 refer 条目一律拒绝。
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

    /** 注册上传配置启动期顾问（校验开关弱化/算法范围笔误/容器限值配平失当打 WARN 留痕）。 */
    @Bean
    public io.github.jevonsnotes.jevokit.api.upload.JSecureUploadStartupAdvisor jsecureUploadStartupAdvisor(
            JevokitApiProperties properties,
            ObjectProvider<org.springframework.boot.autoconfigure.web.servlet.MultipartProperties> multipartProperties) {
        return new io.github.jevonsnotes.jevokit.api.upload.JSecureUploadStartupAdvisor(properties, multipartProperties);
    }

    /** 注册请求体解密适配器（协议入站链路；afterBodyRead 阶段无感执行上传校验）。 */
    @Bean
    public DecryptRequestAdviceAdapter decryptRequestAdviceAdapter(JevokitApiProperties properties,
                                                                   IPreventReplayAttack iPreventReplayAttack,
                                                                   JSecureUploadVerifier uploadVerifier){
        return new DecryptRequestAdviceAdapter(properties,iPreventReplayAttack,uploadVerifier);
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

    /** 注册上传无感校验器（refer 钩子经 ObjectProvider 惰性发现，业务可不注册）。 */
    @Bean
    public JSecureUploadVerifier jSecureUploadVerifier(ObjectProvider<JSecureUploadFileRefChecker> refCheckerProvider){
        return new JSecureUploadVerifier(refCheckerProvider);
    }

    /** 注册 pull 模式文件拉取器（业务显式调用，不做时机决策）。 */
    @Bean
    public JSecureFileFetcher jSecureFileFetcher(){
        return new JSecureFileFetcher();
    }

    /** 注册上传裸奔检测防线（启动期扫描 handler 方法；无 Web 环境无 RequestMappingHandlerMapping 时跳过）。 */
    @Bean
    public SmartInitializingSingleton jSecureUploadGuard(ObjectProvider<RequestMappingHandlerMapping> handlerMappingProvider){
        return new JSecureUploadGuard(handlerMappingProvider);
    }

}
