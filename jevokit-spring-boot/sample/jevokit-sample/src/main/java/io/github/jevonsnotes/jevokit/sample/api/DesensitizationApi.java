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
/**
    * 切面脱敏演示：@Desensitization 标注方法返回对象自动脱敏 + LogBuilder 结构化日志。
    * @param testVo 请求体
    * @return 脱敏后回显
    */
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
/**
    * 属性级脱敏演示：@DstProperty(type=MOBILE, level=LV0) 内联声明。
    * @param testVo 请求体
    * @return 脱敏结果文本
    */
    public String test2( @RequestBody TestVo testVo){
        return "15818266574";
    }

    @RequestMapping("/test3")
/**
    * DesHelper 显式调用演示：深度克隆后脱敏（不依赖切面）。
    * @param testVo 请求体
    * @return 脱敏副本
    */
    public TestVo test3(@RequestBody TestVo testVo){

        return desHelper.desensitize(CollectionUtil.toList(testVo)).get(0);
    }
    @RequestMapping("/test4")
    @ResponseBody
/**
    * logback 脱敏 appender 演示：参数化日志中的对象自动脱敏（DesensitizedConsoleAppender）。
    * @param testVo 请求体
    * @return 原样回显（脱敏发生在日志侧）
    */
    public TestVo testLogDesensitized(@RequestBody TestVo testVo){
        dstLog.info("vo {}",testVo);
        return testVo;
    }
    @RequestMapping("/test5")
    @ResponseBody
/**
    * logback 字符串脱敏演示：xml/json 结构字符串的入参日志脱敏。
    * @param content 结构化字符串
    * @return 原样回显
    */
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
