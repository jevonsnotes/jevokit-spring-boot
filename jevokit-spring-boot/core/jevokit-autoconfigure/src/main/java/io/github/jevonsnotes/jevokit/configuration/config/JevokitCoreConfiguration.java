package io.github.jevonsnotes.jevokit.configuration.config;

import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.jevonsnotes.jevokit.configuration.crypto.CryptoProperties;
import io.github.jevonsnotes.jevokit.core.chain.StringPropertiesSupportChain;
import io.github.jevonsnotes.jevokit.core.editor.StringPropertiesEditor;
import io.github.jevonsnotes.jevokit.core.util.SecurityUtil;

import java.util.List;

/**
 * jevokit-core 的 Spring 装配入口：把字符串编辑链、hutool SpringUtil、加解密密钥初始化装进容器。
 * <p>装配内容：
 * stringPropertiesEditor——收集容器内全部 {@link StringPropertiesSupportChain} 实现（各安全 starter 提供的清洗链）
 * 组成 StringPropertiesEditor，作用于 Spring 属性绑定的字符串进出方向；
 * springUtil——hutool 的静态 Bean 获取器（@ConditionalOnMissingBean 允许应用自备）；
 * cryptoKeyInitializer——容器就绪后把 jevokit.crypto.* 配置灌入 SecurityUtil（详见方法注释）。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(CryptoProperties.class)
public class JevokitCoreConfiguration {

    /**
     * 装配字符串属性编辑器：按容器内 chain 注册顺序串成处理链
     *
     * @param chains 容器内全部 StringPropertiesSupportChain 实现（可为空列表，此时编辑器直通）
     * @return 组装好的编辑器 Bean
     */
    @Bean
    public StringPropertiesEditor stringPropertiesEditor(List<StringPropertiesSupportChain> chains){
        return new StringPropertiesEditor(chains);
    }

    /**
     * 装配 hutool SpringUtil（静态工具取 Bean 的入口），应用已自备时让位
     *
     * @return SpringUtil 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public SpringUtil springUtil(){
        return new SpringUtil();
    }

    /**
     * 将 jevokit.crypto.* 配置注入 SecurityUtil（原内置默认密钥已移除）。
     * 各算法独立生效：未配置的算法对应无参方法在使用时抛 IllegalStateException。
     */
    @Bean
    public InitializingBean cryptoKeyInitializer(CryptoProperties cryptoProperties) {
        return () -> {
            SecurityUtil.init(cryptoProperties.getRsaPublicKey(), cryptoProperties.getRsaPrivateKey(),
                    cryptoProperties.getAesKey(), cryptoProperties.getSm4Key());
            log.info("SecurityUtil crypto keys initialized: rsa-public-key={}, rsa-private-key={}, aes-key={}, sm4-key={}",
                    cryptoProperties.getRsaPublicKey() != null,
                    cryptoProperties.getRsaPrivateKey() != null,
                    cryptoProperties.getAesKey() != null,
                    cryptoProperties.getSm4Key() != null);
        };
    }

}
