package io.github.jevonsnotes.jevokit.sample.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import io.github.jevonsnotes.jevokit.core.annotation.AuditLog;
import io.github.jevonsnotes.jevokit.desensitization.helper.DesHelper;
import io.github.jevonsnotes.jevokit.sample.service.SimpleService;
import io.github.jevonsnotes.jevokit.sample.vo.TestVo;

/**
 * @Description TODO
 * @Date 2022/7/11 15:37
 * @Version 0.0.1
 */
@RestController
@RequestMapping("/audit-log")
@RequiredArgsConstructor
public class AuditLogApi {

    private final DesHelper desHelper;
    private final SimpleService simpleService;
    @AuditLog(eventType = "testType",eventName = "测试事件")
    @RequestMapping("/test")
    @ResponseBody
    public TestVo test(@RequestBody TestVo testVo){
        return simpleService.testInnerFunc(testVo);
    }



}
