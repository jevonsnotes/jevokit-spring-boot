package springbootkit.jasypt.config;

import com.ulisesbocchio.jasyptspringboot.EncryptablePropertyDetector;
import com.ulisesbocchio.jasyptspringboot.EncryptablePropertyResolver;
import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import springbootkit.core.consts.JasyptConst;
import springbootkit.jasypt.encryptor.SpringBootKitStringEncryptor;
import springbootkit.jasypt.resolver.SpringBootKitEncryptPropertyResolver;
import springbootkit.jasypt.util.JasyptUtil;

/**
 * @Description TODO
 * @Date 2022/5/24 16:17
 * @Version 0.0.1
 */

@Configuration
@Slf4j
public class SpringBootKitJasyptConfiguration {

    @Bean(name = JasyptConst.ENCRYPTOR_NAME)
    public StringEncryptor springBootKitStringEncryptor(Environment e) {
        return new SpringBootKitStringEncryptor(e);
    }
    @Bean
    public EncryptablePropertyResolver springBootKitEncryptPropertyResolver(@Qualifier(JasyptConst.ENCRYPTOR_NAME) StringEncryptor encryptor, EncryptablePropertyDetector detector) {
        return new SpringBootKitEncryptPropertyResolver(encryptor,detector);
    }

    @Bean
    public JasyptUtil jasyptUtil(@Qualifier(JasyptConst.ENCRYPTOR_NAME) StringEncryptor encryptor, EncryptablePropertyDetector detector) {
        return new JasyptUtil(encryptor,detector);
    }
}
