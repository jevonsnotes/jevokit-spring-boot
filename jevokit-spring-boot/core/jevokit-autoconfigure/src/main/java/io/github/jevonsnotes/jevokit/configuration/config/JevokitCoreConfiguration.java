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
 * @Description TODO
 * @Date 2022/5/24 16:17
 * @Version 0.0.1
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(CryptoProperties.class)
public class JevokitCoreConfiguration {

    @Bean
    public StringPropertiesEditor stringPropertiesEditor(List<StringPropertiesSupportChain> chains){
        return new StringPropertiesEditor(chains);
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringUtil springUtil(){
        return new SpringUtil();
    }

    /**
     * 将 spkit.crypto.* 配置注入 SecurityUtil（原内置默认密钥已移除）。
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
