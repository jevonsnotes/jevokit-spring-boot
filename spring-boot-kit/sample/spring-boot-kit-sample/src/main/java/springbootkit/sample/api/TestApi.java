package springbootkit.sample.api;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.IoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import springbootkit.core.annotation.AuditLog;
import springbootkit.core.exception.SecurityException;
import springbootkit.core.util.SecureRandomUtil;
import springbootkit.security.code.inject.util.CodeInjectUtil;
import springbootkit.core.lang.ApiResponse;
import springbootkit.sample.vo.TestVo;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("test")
@Slf4j
public class TestApi {
    /**
     * form  xss验证
     * @param content
     * @return
     */
    @RequestMapping("xss/form")
    public String xssTest( String content){
        log.info(String.valueOf(CodeInjectUtil.isSafe(content)));
        return content+"javascript:";
    }

    @RequestMapping("xss/file")
    public String xssTest(@RequestPart("file") MultipartFile multipartFile){
        byte[] bytes = null;
        try {
            bytes = IoUtil.readBytes(multipartFile.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Base64.encode(bytes);
    }

    @RequestMapping("xss/form/res")
    public List<TestVo> xssResTest(@RequestParam String content){
        log.info(content);
        List<TestVo> vos = new ArrayList<>();
        TestVo testVo1 = new TestVo();
        testVo1.setContent("<test>"+content);
        TestVo testVo2 = new TestVo();
        testVo2.setContent("test"+content);
        vos.add(testVo1);
        vos.add(testVo2);
        return vos;
    }
    @RequestMapping("xss/form/res2")
    public ApiResponse<TestVo> xssResTest2(@RequestParam String content){
        List<TestVo> vos = new ArrayList<>();
        TestVo testVo1 = new TestVo();
        testVo1.setContent("<test>"+content);
        TestVo testVo2 = new TestVo();
        testVo2.setContent("test"+content);
        vos.add(testVo1);
        vos.add(testVo2);
        return ApiResponse.ok(testVo1);
    }
    @RequestMapping("xss/json")
    public TestVo xssTest(@RequestBody TestVo vo){
        return vo;
    }
    @RequestMapping("xss/json/list")
    public List<TestVo> xsslistTest(@RequestBody List<TestVo> vos){
        return vos;
    }

    @RequestMapping("xss/form/exclude")
    public String xssExcludeTest(@RequestParam String content){
        return content;
    }

    @ExceptionHandler(SecurityException.class)
    public String XssExceptionHandler(SecurityException e){
        return e.getMessage();
    }
}
