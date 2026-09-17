package io.github.jevonsnotes.jevokit.security.web.fixed.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.jevonsnotes.jevokit.security.web.fixed.aspect.SpringWebFixedAspect;

/**
 * Web 安全加固装配类：注册 {@link SpringWebFixedAspect}，用于封堵 Spring 已停维护组件的已知反序列化 RCE 漏洞。
 * 使用方式：引入本 starter 即无条件生效（无开关——漏洞入口一旦存在就必须封死），无需任何配置。
 * <p>关键约束：当前封堵 CVE-2016-1000027（HttpInvokerServiceExporter 反序列化远程代码执行）；
 * 如业务确需该组件，只能整体移除本 starter，不支持按 URL/Bean 粒度放开。
 */
@Configuration
@Slf4j
public class SpringWebFixedConfiguration {

    @Bean
    public SpringWebFixedAspect springWebFixedAspect(){
        return new SpringWebFixedAspect();
    }

}
