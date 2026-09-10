package springbootkit.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springbootkit.api.advice.DecryptRequestAdviceAdapter;
import springbootkit.api.advice.EncryptResponseAdviceAdapter;
import springbootkit.api.service.IPreventReplayAttack;
import springbootkit.api.service.impl.DefaultPreventReplayAttack;
import springbootkit.api.util.JSecureApiUtil;

/**
 * @Description TODO
 * @Date 2022/5/24 16:17
 * @Version 0.0.1
 */
@Configuration
@Slf4j
public class SpringBootKitApiConfiguration {


    @RefreshScope
    @Bean
    public SpringBootKitApiProperties springBootKitApiProperties(){
        return new SpringBootKitApiProperties();
    }
    @Bean
    public SpringBootKitApiWebMvcConfigurer springBootKitApiWebMvcConfigurer(SpringBootKitApiProperties properties){
        return new SpringBootKitApiWebMvcConfigurer(properties);
    }

    @Bean
    public DecryptRequestAdviceAdapter decryptRequestAdviceAdapter(SpringBootKitApiProperties properties,IPreventReplayAttack iPreventReplayAttack){
        return new DecryptRequestAdviceAdapter(properties,iPreventReplayAttack);
    }

    @Bean
    public EncryptResponseAdviceAdapter encryptResponseAdviceAdapter(SpringBootKitApiProperties properties){
        return new EncryptResponseAdviceAdapter(properties);
    }
    @Bean
    public JSecureApiUtil jSecureApiUtil(SpringBootKitApiProperties properties){
        return new JSecureApiUtil(properties);
    }
    @ConditionalOnMissingBean(IPreventReplayAttack.class)
    @Bean
    public IPreventReplayAttack iPreventReplayAttack(){
        return new DefaultPreventReplayAttack();
    }

}
