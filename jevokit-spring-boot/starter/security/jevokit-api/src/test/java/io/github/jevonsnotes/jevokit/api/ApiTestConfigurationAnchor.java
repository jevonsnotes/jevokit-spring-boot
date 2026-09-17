package io.github.jevonsnotes.jevokit.api;

import org.springframework.boot.SpringBootConfiguration;

/**
 * 库模块切片测试锚点：@WebMvcTest 需要从测试包向上找到一个 @SpringBootConfiguration
 * 作为上下文入口（库模块没有 @SpringBootApplication）。此处仅作锚点，不放任何
 * 自动装配/组件——切片所需配置由测试类上的 @Import 显式引入。
 */
@SpringBootConfiguration
public class ApiTestConfigurationAnchor {
}
