package io.github.jevonsnotes.jevokit.configuration.config;

import cn.hutool.core.util.StrUtil;
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

import java.util.ArrayList;
import java.util.List;

/**
 * jevokit-core 的 Spring 装配入口：把字符串编辑链、hutool SpringUtil、加解密密钥初始化装进容器。
 * <p>装配内容：
 * stringPropertiesEditor——收集容器内全部 {@link StringPropertiesSupportChain} 实现（各安全 starter 提供的清洗链）
 * 组成 StringPropertiesEditor，作用于 Spring 属性绑定的字符串进出方向；
 * springUtil——hutool 的静态 Bean 获取器（@ConditionalOnMissingBean 允许应用自备）；
 * cryptoKeyInitializer——容器就绪后把 jevokit.crypto.* 配置灌入 SecurityUtil，
 * 并按 jevokit.crypto.startup-check（默认 true）做启动期缺密钥校验（详见方法注释）。
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
     * 将 jevokit.crypto.* 配置注入 SecurityUtil（原内置默认密钥已移除），随后按开关执行启动校验。
     * 各算法独立生效：未配置的算法对应无参方法在使用时抛 IllegalStateException。
     * <p>启动校验（jevokit.crypto.startup-check，默认 true）：init 后检查四个密钥，任一缺失（含空白值，
     * 与 init 忽略空白参数的语义对齐）即抛 IllegalStateException 使启动失败，异常消息一次性列出全部
     * 缺失配置键并提示关闭方式——避免"配漏一个键，生产运行期才逐个爆"的挤牙膏式延迟失败。
     * 设计取舍：默认从严（fail-fast 优于零负担，密钥配漏属装配错误，应在上线前暴露）；
     * 确实只用部分算法、不接受启动被拦的应用可配 startup-check=false 退回"用到才失败"的现状行为。
     */
    @Bean
    public InitializingBean cryptoKeyInitializer(CryptoProperties cryptoProperties) {
        return () -> {
            SecurityUtil.init(cryptoProperties.getRsaPublicKey(), cryptoProperties.getRsaPrivateKey(),
                    cryptoProperties.getAesKey(), cryptoProperties.getSm4Key());
            if (cryptoProperties.isStartupCheck()) {
                List<String> missingKeys = missingCryptoKeys(cryptoProperties);
                if (!missingKeys.isEmpty()) {
                    throw new IllegalStateException("jevokit.crypto startup check failed, missing keys: "
                            + String.join(", ", missingKeys)
                            + ". Configure them (env / external config, not committed), or set"
                            + " jevokit.crypto.startup-check=false to skip this check and defer"
                            + " the failure to first use.");
                }
            }
            log.info("SecurityUtil crypto keys initialized: rsa-public-key={}, rsa-private-key={}, aes-key={}, sm4-key={}",
                    cryptoProperties.getRsaPublicKey() != null,
                    cryptoProperties.getRsaPrivateKey() != null,
                    cryptoProperties.getAesKey() != null,
                    cryptoProperties.getSm4Key() != null);
        };
    }

    /**
     * 收集全部缺失（null 或空白）的 crypto 配置键，一次性返回供启动校验报错
     *
     * @param cryptoProperties crypto 配置
     * @return 缺失配置键清单（如 jevokit.crypto.aes-key）；全部已配置时为空列表
     */
    private List<String> missingCryptoKeys(CryptoProperties cryptoProperties) {
        List<String> missing = new ArrayList<>(4);
        if (StrUtil.isBlank(cryptoProperties.getRsaPublicKey())) {
            missing.add("jevokit.crypto.rsa-public-key");
        }
        if (StrUtil.isBlank(cryptoProperties.getRsaPrivateKey())) {
            missing.add("jevokit.crypto.rsa-private-key");
        }
        if (StrUtil.isBlank(cryptoProperties.getAesKey())) {
            missing.add("jevokit.crypto.aes-key");
        }
        if (StrUtil.isBlank(cryptoProperties.getSm4Key())) {
            missing.add("jevokit.crypto.sm4-key");
        }
        return missing;
    }

}
