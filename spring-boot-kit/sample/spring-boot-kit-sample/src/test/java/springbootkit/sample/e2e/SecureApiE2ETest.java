package springbootkit.sample.e2e;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import springbootkit.api.dto.JSecureResponse;
import springbootkit.api.util.JSecureApiUtil;
import springbootkit.sample.SpringBootKitSampleApplication;
import springbootkit.sample.vo.TestVo;

import java.util.UUID;

/**
 * api 模块（JSecureApi）E2E 正向链路（原 MockTest.testSecureApi 拆分）。
 *
 * <p>环境要求（@Tag("env")）：默认构建不跑（surefire excludedGroups=env）。
 * 客户端签名/防重放/加解密由 JSecureApiUtil 构造，经 MockMvc 打到本应用
 * /secure/test，验证服务端拦截器-加解密-响应链路（负路径切片测试见 api 模块）。
 */
@Slf4j
@Tag("env")
@DisplayName("SecureApi 端到端")
@SpringBootTest(classes = SpringBootKitSampleApplication.class)
@AutoConfigureMockMvc
class SecureApiE2ETest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("JSecureApiUtil 构造合规请求：/secure/test 验签通过并正常解密响应")
    void secureApiHappyPathThroughMockMvc() {
        // given
        TestVo demo = TestVo.builder().name("demo").build();
        // when：客户端执行器把请求打到 MockMvc（真实签名头 + 请求体加密）
        JSecureResponse<TestVo> response = JSecureApiUtil
                .getExecutor("gz_demo", "/secure/test", demo, TestVo.class)
                .transNo(UUID.randomUUID().toString())
                .transType("TN0000001")
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
