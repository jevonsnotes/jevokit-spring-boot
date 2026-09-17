package io.github.jevonsnotes.jevokit.desensitization.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import io.github.jevonsnotes.jevokit.core.annotation.AuditLog;
import io.github.jevonsnotes.jevokit.core.lang.ApiResponse;
import io.github.jevonsnotes.jevokit.core.lang.SimpleData;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstAlgorithm;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstConst;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstType;
import io.github.jevonsnotes.jevokit.desensitization.dto.DstContext;
import io.github.jevonsnotes.jevokit.desensitization.util.DesUtil;

/**
 * 脱敏服务内置端点（/jevokit/desensitization/decrypt）：提交含 {@code @DST(...)} 密文的文本，
 * 按当前用户权限还原明文（{@link DesUtil#decrypt(String, boolean)}，ignorePower=false）。
 * 使用方式：POST JSON {@code {"data":"含密文文本"}}，响应 data 为还原结果；
 * 无权限（密文级别超出用户权限）时 fail 并回显原文（密文不丢）。
 * <p>关键约束：该端点可直接还原敏感密文，默认无独立鉴权（配套 token 拦截器未启用，
 * 见 DesensitizationApiProperties 注释）——对外暴露前必须由网关/全局鉴权兜住。
 */
@Slf4j
@RestController
@RequestMapping("/jevokit/desensitization")
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
