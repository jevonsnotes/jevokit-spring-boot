package io.github.jevonsnotes.jevokit.sample.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import io.github.jevonsnotes.jevokit.core.lang.CallbackR;
import io.github.jevonsnotes.jevokit.core.lang.LockKey;
import io.github.jevonsnotes.jevokit.core.util.CollectionUtil;
import io.github.jevonsnotes.jevokit.core.util.JsonUtil;
import io.github.jevonsnotes.jevokit.core.util.LockUtil;
import io.github.jevonsnotes.jevokit.core.util.XmlUtil;
import io.github.jevonsnotes.jevokit.desensitization.annotation.DstProperty;
import io.github.jevonsnotes.jevokit.desensitization.annotation.Desensitization;
import io.github.jevonsnotes.jevokit.desensitization.builder.log.LogBuilder;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstProcessType;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstType;
import io.github.jevonsnotes.jevokit.desensitization.helper.DesHelper;
import io.github.jevonsnotes.jevokit.desensitization.util.DesUtil;
import io.github.jevonsnotes.jevokit.sample.vo.TestVo;

import java.util.HashMap;
import java.util.Map;

/**
 * 脱敏演示端点（/desensitization/*）：/test 走 @Desensitization 切面 + LogBuilder 结构化
 * 日志脱敏；/test2 演示 @DstProperty 属性级声明。请求体同时被 spkit.api.access 的
 * token 访问控制覆盖（见 application.yml tokens 段）。
 */
@Slf4j
@RestController
@RequestMapping("/desensitization")
@RequiredArgsConstructor
public class DesensitizationApi {
    Logger dstLog = LoggerFactory.getLogger("DST_LOG");

    private final DesHelper desHelper;
    @Desensitization()
    @RequestMapping("/test")
    public TestVo test(@RequestBody TestVo testVo){
        LockKey abc = LockUtil.getKey("Abc");
        Map<String,Object> temp = new HashMap<>();
        temp.put("phone","13800000000");
        String msg = LogBuilder.create().meta("name", "张三").meta("phone", "15818222222").meta("test",testVo).metaJson("data",testVo).build();
        log.info(msg);
        dstLog.info("vo {}",testVo);
        return testVo;
    }

    @DstProperty(type = DstType.MOBILE,level = DstLevel.LV0)
    @RequestMapping("/test2")
    @ResponseBody
    public String test2( @RequestBody TestVo testVo){
        return "15818266574";
    }

    @RequestMapping("/test3")
    public TestVo test3(@RequestBody TestVo testVo){

        return desHelper.desensitize(CollectionUtil.toList(testVo)).get(0);
    }
    @RequestMapping("/test4")
    @ResponseBody
    public TestVo testLogDesensitized(@RequestBody TestVo testVo){
        dstLog.info("vo {}",testVo);
        return testVo;
    }
    @RequestMapping("/test5")
    @ResponseBody
    public String testLogDesensitized2(@RequestBody String content){
        if(XmlUtil.isXml(content)){
            String des = DesUtil.desByProcessTypeWrap(DstProcessType.LOG, () -> DesUtil.desensitizeXml(content));
        } else if(JsonUtil.isTypeJSON(content)){
            String des = DesUtil.desByProcessTypeWrap(DstProcessType.LOG, () -> DesUtil.desensitizeJson(content));
        }
        log.info("vo {}",content);
        dstLog.info("vo {}",content);

        return content;
    }

}
