package springbootkit.sample.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import springbootkit.core.annotation.AuditLog;

/**
 * @Description TODO
 * @Date 2024/7/1 9:53
 * @Version 0.0.1
 */
@Service
@Slf4j
public class SimpleService {

    @AuditLog(eventType = "testType",eventName = "调用函数测试事件")
    public <T>T testInnerFunc(T vo){
        return vo;
    }
}
