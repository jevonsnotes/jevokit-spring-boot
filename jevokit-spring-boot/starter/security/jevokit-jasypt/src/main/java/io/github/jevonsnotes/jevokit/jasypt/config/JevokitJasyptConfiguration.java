package io.github.jevonsnotes.jevokit.jasypt.config;

import com.ulisesbocchio.jasyptspringboot.EncryptablePropertyDetector;
import com.ulisesbocchio.jasyptspringboot.EncryptablePropertyResolver;
import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import io.github.jevonsnotes.jevokit.core.consts.JasyptConst;
import io.github.jevonsnotes.jevokit.jasypt.encryptor.JevokitStringEncryptor;
import io.github.jevonsnotes.jevokit.jasypt.resolver.JevokitEncryptPropertyResolver;
import io.github.jevonsnotes.jevokit.jasypt.util.JasyptUtil;

/**
 * jasypt 集成装配类：注册配置解密所需的三件套——命名加密器（jevokitStringEncryptor）、
 * 属性解析器与静态工具门面 {@link JasyptUtil}，替代 jasypt-spring-boot-starter 的默认装配。
 * 使用方式：引入本 starter 后，yml 中以 {@code ENC(...)} 包裹的密文属性在绑定阶段自动解密；
 * 口令经 {@code jasypt.encryptor.password}（或 {@code jasypt.encryptor.jep}）提供，缺失时启动 fail-fast。
 * <p>关键约束：加密器参数（算法/迭代次数/盐与 IV 生成器等）沿用 jasypt 标准前缀
 * {@code jasypt.encryptor.*}，默认值与 jasypt 官方 starter 一致（PBEWithMD5AndDES、base64 输出），
 * 保证既有密文可平滑迁移。
 */

@Configuration
@Slf4j
public class JevokitJasyptConfiguration {

    @Bean(name = JasyptConst.ENCRYPTOR_NAME)
    public StringEncryptor jevokitStringEncryptor(Environment e) {
        return new JevokitStringEncryptor(e);
    }
    @Bean
    public EncryptablePropertyResolver jevokitEncryptPropertyResolver(@Qualifier(JasyptConst.ENCRYPTOR_NAME) StringEncryptor encryptor, EncryptablePropertyDetector detector) {
        return new JevokitEncryptPropertyResolver(encryptor,detector);
    }

    @Bean
    public JasyptUtil jasyptUtil(@Qualifier(JasyptConst.ENCRYPTOR_NAME) StringEncryptor encryptor, EncryptablePropertyDetector detector) {
        return new JasyptUtil(encryptor,detector);
    }
}
