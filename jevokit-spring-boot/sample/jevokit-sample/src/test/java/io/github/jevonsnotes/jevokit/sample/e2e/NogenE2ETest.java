package io.github.jevonsnotes.jevokit.sample.e2e;

import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import io.github.jevonsnotes.jevokit.sample.JevokitSampleApplication;

/**
 * nogen 模块 E2E（原 MockTest.testNoGen / testNoGenRecycle 拆分）。
 *
 * <p>默认测试集（无需 MQ）：内建 @ActiveProfiles("local") 排除 RocketMQ 自动装配，环境仅需 Redis 在位 + H2 内存自给。
 *
 *
 * <p>事务说明：单号获取走号段模式 + 真实 DB 提交，测试以 Propagation.NEVER
 * 禁用回滚（与原 MockTest 一致）；每次用随机 bizTag 插入独立行，用例间零共享数据。
 */
@Slf4j
@ActiveProfiles("local")
@DisplayName("nogen 端到端")
@SpringBootTest(classes = JevokitSampleApplication.class)
@AutoConfigureMockMvc
class NogenE2ETest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbcTemplate;

    private String seedBizNo(String scene) {
        String bizNo = "demo" + RandomUtil.randomString(8);
        log.info("nogen {}：业务类型 {}", scene, bizNo);
        jdbcTemplate.update("INSERT INTO biz_no (biz_tag, max_id, step, mode, threhold, description) VALUES ('"
                + bizNo + "', 0, 10000, 1, '70.00', '单号" + scene + "测试demo')");
        return bizNo;
    }

    @Test
    @DisplayName("POST /nogen/test：10 万次循环取号全部成功")
    @Transactional(propagation = Propagation.NEVER) // 禁用事务，让数据提交
    void nogenFetchLoopSucceeds() throws Exception {
        // given
        String bizNo = seedBizNo("获取");
        // when / then
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/nogen/test")
                        .queryParam("loop", "100000")
                        .queryParam("bizNo", bizNo))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string("success"))
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    @DisplayName("POST /nogen/testRecycle：10 万次循环取号+回收全部成功")
    @Transactional(propagation = Propagation.NEVER)
    void nogenRecycleLoopSucceeds() throws Exception {
        // given
        String bizNo = seedBizNo("回收");
        // when / then
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/nogen/testRecycle")
                        .queryParam("loop", "100000")
                        .queryParam("bizNo", bizNo))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string("success"))
                .andDo(MockMvcResultHandlers.print());
    }
}
