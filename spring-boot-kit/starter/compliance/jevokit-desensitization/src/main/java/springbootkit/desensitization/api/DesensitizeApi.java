package springbootkit.desensitization.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import springbootkit.core.annotation.AuditLog;
import springbootkit.core.lang.ApiResponse;
import springbootkit.core.lang.SimpleData;
import springbootkit.desensitization.consts.DstAlgorithm;
import springbootkit.desensitization.consts.DstConst;
import springbootkit.desensitization.consts.DstType;
import springbootkit.desensitization.dto.DstContext;
import springbootkit.desensitization.util.DesUtil;

/**
 * @Description TODO
 * @Date 2024/11/22
 * @Version 0.0.1
 */
@Slf4j
@RestController
@RequestMapping("/spring-boot-kit/desensitization")
@RequiredArgsConstructor
public class DesensitizeApi {

    @RequestMapping("/decrypt")
    @ResponseBody
    public ApiResponse<String> decrypt(@RequestBody SimpleData<String> vo){
        try {
            return ApiResponse.ok(DesUtil.decrypt(vo.getData(),false));
        } catch (Exception e) {
            log.debug(e.getMessage(),e);
            return ApiResponse.fail(e.getMessage(),vo.getData());
        }
    }

//    @RequestMapping("/encrypt")
//    public String encrypt(@RequestBody String content){
//        try {
//            DstContext dstContext = DstContext.builder().type(DstType.CUSTOM).build();
//            String des = DesUtil.des(content, dstContext);
//            des = des.substring(des.indexOf(DstConst.DESENSITIZED_ENC_CONTENT_PREFIX)+1);
//            des = des.substring(0,des.lastIndexOf(DstConst.DESENSITIZED_ENC_CONTENT_SUFFIX));
//
//            return des;
//        } catch (Exception e) {
//            log.error(e.getMessage(),e);
//            return "文本加密失败";
//        }
//    }

}
