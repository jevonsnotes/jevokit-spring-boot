package io.github.jevonsnotes.jevokit.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class JevokitSampleApplication {

    /**
    * 示例应用入口（全 starter 聚合演示的载体）。
    * @param args 启动参数
    */
    public static void main(String[] args) {
        SpringApplication.run(JevokitSampleApplication.class, args);
    }


    @Bean
    /**
    * 演示用 RestTemplate Bean。
    * @return 默认配置实例
    */
    public RestTemplate restTemplate(){
        return new RestTemplate();
    }
}
