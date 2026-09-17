package io.github.jevonsnotes.jevokit.sample.api;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.IoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.github.jevonsnotes.jevokit.core.annotation.AuditLog;
import io.github.jevonsnotes.jevokit.core.exception.SecurityException;
import io.github.jevonsnotes.jevokit.core.util.SecureRandomUtil;
import io.github.jevonsnotes.jevokit.security.code.inject.util.CodeInjectUtil;
import io.github.jevonsnotes.jevokit.core.lang.ApiResponse;
import io.github.jevonsnotes.jevokit.sample.vo.TestVo;

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
/**
    * form 参数 XSS 清洗演示：拦截器清洗后到达，回显附加风险串供验证。
    * @param content 表单参数
    * @return 处理后内容
    */
    public String xssTest( String content){
        log.info(String.valueOf(CodeInjectUtil.isSafe(content)));
        return content+"javascript:";
    }

    @RequestMapping("xss/file")
/**
    * 文件上传 XSS 演示：文件体 Base64 回显（验证文件通道的清洗边界）。
    * @param multipartFile 上传文件
    * @return Base64 文本
    */
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
/**
    * 响应侧 XSS 演示：返回对象列表经响应清洗（ignore-response 开启时生效）。
    * @param content 注入内容
    * @return 构造的响应列表
    */
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
/**
    * 响应侧 XSS 演示（包装体形态）。
    * @param content 注入内容
    * @return 统一响应
    */
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
/**
    * JSON 体 XSS 清洗演示。
    * @param vo 请求体
    * @return 回显
    */
    public TestVo xssTest(@RequestBody TestVo vo){
        return vo;
    }
    @RequestMapping("xss/json/list")
/**
    * JSON 数组体 XSS 清洗演示。
    * @param vos 请求体列表
    * @return 回显
    */
    public List<TestVo> xsslistTest(@RequestBody List<TestVo> vos){
        return vos;
    }

    @RequestMapping("xss/form/exclude")
/**
    * 排除路径演示：该路径在 url-exclude-patterns 中，不做清洗原样返回。
    * @param content 原始内容
    * @return 原样内容
    */
    public String xssExcludeTest(@RequestParam String content){
        return content;
    }

    @ExceptionHandler(SecurityException.class)
/**
    * reject 模式异常处理演示：拒绝访问抛 SecurityException 时的兜底回显。
    * @param e 安全异常
    * @return 异常消息
    */
    public String XssExceptionHandler(SecurityException e){
        return e.getMessage();
    }
}
