package springbootkit.sample.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import springbootkit.core.lang.CallbackR;
import springbootkit.core.lang.LockKey;
import springbootkit.core.util.CollectionUtil;
import springbootkit.core.util.JsonUtil;
import springbootkit.core.util.LockUtil;
import springbootkit.core.util.XmlUtil;
import springbootkit.desensitization.annotation.DstProperty;
import springbootkit.desensitization.annotation.Desensitization;
import springbootkit.desensitization.builder.log.LogBuilder;
import springbootkit.desensitization.consts.DstLevel;
import springbootkit.desensitization.consts.DstProcessType;
import springbootkit.desensitization.consts.DstType;
import springbootkit.desensitization.helper.DesHelper;
import springbootkit.desensitization.util.DesUtil;
import springbootkit.sample.vo.TestVo;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description TODO
 * @Date 2022/7/11 15:37
 * @Version 0.0.1
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
