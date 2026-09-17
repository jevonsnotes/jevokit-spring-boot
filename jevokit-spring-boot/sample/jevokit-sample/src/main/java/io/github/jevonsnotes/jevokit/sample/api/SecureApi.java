package io.github.jevonsnotes.jevokit.sample.api;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import io.github.jevonsnotes.jevokit.api.annotation.JSecureApi;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;
import io.github.jevonsnotes.jevokit.core.lang.ApiResponse;
import io.github.jevonsnotes.jevokit.core.util.HttpUtil;
import io.github.jevonsnotes.jevokit.core.util.RequestUtil;
import io.github.jevonsnotes.jevokit.okhttp.util.OkHttpUtil;
import io.github.jevonsnotes.jevokit.sample.vo.TestVo;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;

/**
 * @Description TODO
 * @Date 2025/4/10
 * @Version 0.0.1
 */
@Slf4j
@RestController
@RequestMapping("/secure")
@RequiredArgsConstructor
public class SecureApi {

    private final JSecureApiUtil jSecureApiUtil;

    @JSecureApi(transType = {"T030421", StrUtil.EMPTY})
    @RequestMapping("/test")
    @ResponseBody
    public JSecureResponse<TestVo> test(@RequestBody @Valid JSecureRequest<TestVo> testVo){
        HttpUtil.getRemoteIp(JSecureApiUtil.getCurrentRequest().orElseThrow(RuntimeException::new));
        TestVo target = testVo.getTarget();
        TestVo res = new TestVo();
        res.setName(target.getName());
        return JSecureResponse.ok(res);
    }
    @JSecureApi(transType = {"T030421"})
    @RequestMapping("/test4")
    @ResponseBody
    public JSecureResponse<TestVo> test4(@RequestBody @Valid JSecureRequest<TestVo> testVo){
        HttpUtil.getRemoteIp(JSecureApiUtil.getCurrentRequest().orElseThrow(RuntimeException::new));
        TestVo target = testVo.getTarget();
        TestVo res = new TestVo();
        res.setName(target.getName());
        return JSecureResponse.ok(res);
    }

    @RequestMapping("/test2")
    @ResponseBody
    public JSecureResponse<TestVo> test2(@RequestBody @Valid TestVo testVo){
        return JSecureResponse.ok(testVo);
    }
    @RequestMapping("/test3")
    @ResponseBody
    public ApiResponse<String> test2(){

        TestVo data = new TestVo();
        data.setName("demo");

        JSecureResponse<TestVo> gzDemo = JSecureApiUtil
                .getExecutor("gz_demo","http://localhost:18080/secure/test",data,TestVo.class)
                .transNo(UUID.randomUUID().toString())
                .transType("TN0000001")
                .execute((url,json, headers) -> OkHttpUtil.of().postJson(url, json, headers));
//        JSecureResponse<TestVo> res = JSecureApiUtil.postJson("gz_demo", data, TestVo.class, (json, headers) -> OkHttpUtil.of().postJson("http://localhost:18080/secure/test", json, headers));
        TestVo target = gzDemo.getTarget();
        System.out.println(JSON.toJSONString(target));
        return ApiResponse.ok("success");
    }
}
