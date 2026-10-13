package io.github.jevonsnotes.jevokit.sample.e2e;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import io.github.jevonsnotes.jevokit.sample.JevokitSampleApplication;

import java.net.URL;

/**
 * excel 模块 E2E（原 MockTest.testExcel 拆分）。
 *
 * <p>默认测试集（无需 MQ）：内建 @ActiveProfiles("local") 排除 RocketMQ 自动装配，环境仅需 Redis 在位 + H2 内存自给。
 * 用 main resources 里的模板样例文件走 multipart 导入链路。
 */
@Slf4j
@ActiveProfiles("local")
@DisplayName("excel 端到端")
@SpringBootTest(classes = JevokitSampleApplication.class)
@AutoConfigureMockMvc
class ExcelE2ETest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("multipart 导入 importExcel.xlsx：解析成功返回 status=0")
    void excelImportParsesTemplate() throws Exception {
        // given：classpath 模板样例（src/main/resources/tpl/excel/importExcel.xlsx）
        URL resource = ResourceUtil.getResource("tpl/excel/importExcel.xlsx");
        MockPart part = new MockPart("file1",
                "importExcel.xlsx",
                IoUtil.readBytes(resource.openStream()));
        part.getHeaders().setContentType(MediaType.MULTIPART_FORM_DATA);

        // when / then
        String content = mockMvc.perform(MockMvcRequestBuilders
                        .multipart("/excel/importExcel")
                        .part(part)
                        .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("0"))
                .andDo(MockMvcResultHandlers.print())
                .andReturn()
                .getResponse()
                .getContentAsString();
        log.info("excel import response {}", JSONObject.toJSONString(content));
    }
}
