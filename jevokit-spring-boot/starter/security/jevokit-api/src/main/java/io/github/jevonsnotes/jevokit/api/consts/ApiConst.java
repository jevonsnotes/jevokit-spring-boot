package io.github.jevonsnotes.jevokit.api.consts;

/**
 * JSecure API 协议常量：安全接口访问控制使用的保留请求头名。
 * 关键约束：该头名被 {@code JevokitApiAccessInterceptor} 消费（简单 token 准入，jevokit.api.access.* 配置），
 * 业务侧不应占用同名自定义头。
 */
public class ApiConst {
    public static final String API_HEADER_ACCESS_TOKEN = "Spring-Boot-Kit-Api-Access-Token";
}
