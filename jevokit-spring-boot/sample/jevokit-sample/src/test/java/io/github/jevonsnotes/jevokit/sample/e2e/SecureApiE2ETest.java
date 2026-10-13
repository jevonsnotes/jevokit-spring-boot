package io.github.jevonsnotes.jevokit.sample.e2e;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiClientProperties;
import io.github.jevonsnotes.jevokit.api.config.JevokitApiProperties;
import io.github.jevonsnotes.jevokit.api.dto.JSecureResponse;
import io.github.jevonsnotes.jevokit.api.util.JSecureApiUtil;
import io.github.jevonsnotes.jevokit.sample.JevokitSampleApplication;
import io.github.jevonsnotes.jevokit.sample.vo.TestVo;
import cn.hutool.extra.spring.SpringUtil;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * api 模块（JSecureApi）E2E 正向链路（原 MockTest.testSecureApi 拆分）。
 *
 * <p>默认测试集（无需 MQ）：内建 @ActiveProfiles("local") 排除 RocketMQ 自动装配，环境仅需 Redis 在位 + H2 内存自给。
 * 客户端签名/防重放/加解密由 JSecureApiUtil 构造，经 MockMvc 打到本应用
 * /secure/test，验证服务端拦截器-加解密-响应链路（负路径切片测试见 api 模块）。
 */
@Slf4j
@ActiveProfiles("local")
@DisplayName("SecureApi 端到端")
@SpringBootTest(classes = JevokitSampleApplication.class)
@AutoConfigureMockMvc
class SecureApiE2ETest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("JSecureApiUtil 构造合规请求：/secure/test 验签通过并正常解密响应")
    void secureApiHappyPathThroughMockMvc() {
        // given：JSecureApiUtil 的静态 getClient 走 SpringUtil 拿全局 JevokitApiProperties，
        // 复用应用上下文中的 client-demo-1，但覆盖其 SM4 密钥——演示 secret demo-sm4-secret
        // 非 16 字节，JSRequestExecutor 的 SM4 加密会抛 CryptoException（SM4 128-bit 硬约束）；
        // allow-path 已含 /secure/test，IP 准入（192.0.2.*）在 MockMvc 下取本机回环放行，
        // transType 取 SecureApi @JSecureApi 声明的 T030421。
        // （原 MockTest 的 gz_demo 客户端已随演示配置重构消失，属陈旧 fixture）
        JevokitApiProperties apiProperties = SpringUtil.getBean(JevokitApiProperties.class);
        JevokitApiClientProperties client = apiProperties.getClient().stream()
                .filter(c -> "client-demo-1".equals(c.getId()))
                .findFirst()
                .orElseThrow(NoSuchElementException::new);
        client.setSecretKeySm4("DemoSm4Key123456"); // 16 字节演示密钥（与 jevokit.crypto.sm4-key 同源）
        client.setSecretKey("DemoSm4Key123456");    // 头签名 MD5 同步换钥，保证自洽
        // MockMvc 下 getRemoteIp 解析的是本机回环 127.0.0.1，非演示白名单 192.0.2.*——
        // 放行列表临时并入回环（E2E 关注的是签名/加解密链路而非 IP 门本身）
        client.getAllowIp().add("127.0.0.1");

        TestVo demo = TestVo.builder().name("demo").build();
        JSecureResponse<TestVo> response = JSecureApiUtil
                .getExecutor("client-demo-1", "/secure/test", demo, TestVo.class)
                .transNo(UUID.randomUUID().toString())
                .transType("T030421")
                .execute((url, json, headers) -> {
                    try {
                        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders
                                .post(url)
                                .content(json)
                                .contentType(MediaType.APPLICATION_JSON);
                        headers.forEach(requestBuilder::header);
                        return mockMvc.perform(requestBuilder)
                                .andExpect(MockMvcResultMatchers.status().isOk())
                                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("0"))
                                .andDo(MockMvcResultHandlers.print())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        // then
        log.info("SecureApi response {}", JSON.toJSONString(response));
    }
}
