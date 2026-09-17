package io.github.jevonsnotes.jevokit.sample.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import io.github.jevonsnotes.jevokit.core.annotation.AuditLog;

 /**
 * 内层方法审计演示服务：@AuditLog 注解在非 Controller 托管 Bean 方法上的事件
 * 上报形态（eventType/eventName 自定义），补齐"切面不只拦 Controller"的演示面。
 */
@Service
@Slf4j
public class SimpleService {

    @AuditLog(eventType = "testType",eventName = "调用函数测试事件")
    /**
    * 内层方法审计演示：非 Controller 托管 Bean 上的 @AuditLog 事件上报。
    * @param vo 任意载体
    * @return 原样回传
    */
    public <T>T testInnerFunc(T vo){
        return vo;
    }
}
