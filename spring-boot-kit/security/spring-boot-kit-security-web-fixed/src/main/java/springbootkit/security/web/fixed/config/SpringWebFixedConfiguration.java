package springbootkit.security.web.fixed.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springbootkit.security.web.fixed.aspect.SpringWebFixedAspect;

/**
 * @Description TODO
 * @Date 2022/5/25 10:43
 * @Version 0.0.1
 */
@Configuration
@Slf4j
public class SpringWebFixedConfiguration {

    @Bean
    public SpringWebFixedAspect springWebFixedAspect(){
        return new SpringWebFixedAspect();
    }

}
