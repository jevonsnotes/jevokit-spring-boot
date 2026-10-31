package io.github.jevonsnotes.jevokit.sample.api;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import io.github.jevonsnotes.jevokit.api.annotation.JSecureApi;
import io.github.jevonsnotes.jevokit.api.client.JSecureClient;
import io.github.jevonsnotes.jevokit.api.dto.JSecureRequest;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadDto;
import io.github.jevonsnotes.jevokit.api.dto.JSecureUploadFileDto;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;
import io.github.jevonsnotes.jevokit.core.lang.ApiResponse;
import io.github.jevonsnotes.jevokit.core.util.HttpUtil;
import io.github.jevonsnotes.jevokit.core.util.RequestUtil;
import io.github.jevonsnotes.jevokit.okhttp.util.OkHttpUtil;
import io.github.jevonsnotes.jevokit.sample.vo.TestVo;
import io.github.jevonsnotes.jevokit.sample.vo.UploadOrderVo;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

 /**
 * JSecure 安全接口演示端点（/secure/test*）：@JSecureApi 标注后走加签验签 + SM2/SM4
 * 混合加解密 + 防重放通道，E2E 以 client-demo-1 密钥组做密文往返断言。
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
    /**
    * JSecure 全链演示：请求密文解密→验签→防重放→业务→响应加密（client-demo-1 密钥组）。
    * @param testVo JSecure 请求包装（target 为业务体）
    * @return JSecure 响应包装（通道自动加密）
    */
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
    /**
    * JSecure 演示（client-demo-2 密钥组：AES secret + 不同国密对）。
    * @param testVo JSecure 请求包装
    * @return JSecure 响应包装
    */
    public JSecureResponse<TestVo> test4(@RequestBody @Valid JSecureRequest<TestVo> testVo){
        HttpUtil.getRemoteIp(JSecureApiUtil.getCurrentRequest().orElseThrow(RuntimeException::new));
        TestVo target = testVo.getTarget();
        TestVo res = new TestVo();
        res.setName(target.getName());
        return JSecureResponse.ok(res);
    }

    @JSecureApi(transType = {"T030422"})
    @PostMapping("/upload")
    @ResponseBody
    /**
    * JSecure 文件上传演示（meta + files 双参数形态）：meta part 装加密信封
    * （业务数据 + 文件摘要清单），文件 part 明文传输、由协议链无感校验完整性
    * （push 按摘要比对 / pull 域名白名单 / refer 钩子校验，详见 jevokit-api README）。
    * @param meta 协议信封（业务体 JSecureUploadDto：报销单 + 文件清单）
    * @param files push 模式的文件 part（filename = fileId 对齐；纯 pull/refer 请求无此 part）
    * @return JSecure 响应包装（受理条目数/文件 part 数）
    */
    public JSecureResponse<String> upload(
            @RequestPart("meta") @Valid JSecureRequest<JSecureUploadDto<UploadOrderVo, JSecureUploadFileDto>> meta,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        JSecureUploadDto<UploadOrderVo, JSecureUploadFileDto> upload = meta.getTarget();
        int declared = upload.getFiles() == null ? 0 : upload.getFiles().size();
        int bound = files == null ? 0 : files.size();
        // 走到这里时协议校验已全部通过：摘要清单与文件 part 一一对齐且摘要一致
        log.info("JSecure 上传受理：orderNo={}, 声明文件数={}, 实际文件 part 数={}",
                upload.getBusinessData().getOrderNo(), declared, bound);
        upload.getFiles().forEach(file -> log.info("文件条目：fileId={}, fileType={}, fetchMode={}, fileName={}（仅展示，永不落盘）",
                file.getFileId(), file.getFileType(), file.getFetchMode(), file.getFileName()));
        return JSecureResponse.ok("accepted:" + declared + "/" + bound);
    }

    @RequestMapping("/test2")
    @ResponseBody
    /**
    * 裸业务体入口的 JSecure 形态：无 JSecureRequest 包装同样走安全通道。
    * @param testVo 业务体
    * @return JSecure 响应包装
    */
    public JSecureResponse<TestVo> test2(@RequestBody @Valid TestVo testVo){
        return JSecureResponse.ok(testVo);
    }
    @RequestMapping("/test3")
    @ResponseBody
    /**
    * 普通（非 JSecure）对照端点：无加密通道，演示同一应用内两种形态并存。
    * @return 普通响应包装
    */
    public ApiResponse<String> test2(){

        TestVo data = new TestVo();
        data.setName("demo");

        JSecureResponse<TestVo> gzDemo = JSecureClient.of("gz_demo")
                .post("http://localhost:18080/secure/test", data, TestVo.class)
                .transNo(UUID.randomUUID().toString())
                .transType("TN0000001")
                .execute((url,json, headers) -> OkHttpUtil.of().postJson(url, json, headers));
//        JSecureResponse<TestVo> res = JSecureApiUtil.postJson("gz_demo", data, TestVo.class, (json, headers) -> OkHttpUtil.of().postJson("http://localhost:18080/secure/test", json, headers));
        TestVo target = gzDemo.getTarget();
        System.out.println(JSON.toJSONString(target));
        return ApiResponse.ok("success");
    }
}
