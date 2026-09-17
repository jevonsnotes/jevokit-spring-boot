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
 * @Description TODO
 * @Date 2022/5/24 16:17
 * @Version 0.0.1
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
