package io.github.jevonsnotes.jevokit.sample.e2e;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import io.github.jevonsnotes.jevokit.core.lang.ApiResponse;
import io.github.jevonsnotes.jevokit.sample.JevokitSampleApplication;
import io.github.jevonsnotes.jevokit.sample.vo.DemoTaskStartVo;
import io.github.jevonsnotes.jevokit.workflow.consts.FlowState;
import io.github.jevonsnotes.jevokit.workflow.vo.DeploymentDeployVo;
import io.github.jevonsnotes.jevokit.workflow.vo.DeploymentResource;
import io.github.jevonsnotes.jevokit.workflow.vo.TaskClaimVo;
import io.github.jevonsnotes.jevokit.workflow.vo.TaskCompletedVo;
import io.github.jevonsnotes.jevokit.workflow.vo.TaskInfoVo;
import io.github.jevonsnotes.jevokit.workflow.vo.TaskQueryVo;

import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * workflow（camunda）模块 E2E（原 MockTest.testWorkflow 拆分）。
 *
 * <p>默认测试集（无需 MQ）：内建 @ActiveProfiles("local") 排除 RocketMQ 自动装配，环境仅需 Redis 在位 + H2 内存自给。
 * 需要 H2 + Redis；流程实例真实提交（Propagation.NEVER）。
 * 完整链路：部署 BPMN → 发起 → 起草人提交 → 地市审核认领+通过 → 省级查询，
 * 末尾断言流程实例 ID 前后一致。
 */
@Slf4j
@ActiveProfiles("local")
@DisplayName("workflow 端到端")
@SpringBootTest(classes = JevokitSampleApplication.class)
@AutoConfigureMockMvc
class WorkflowE2ETest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("task_demo_simple_audit 全流程：部署→发起→两级审核，流程 ID 贯穿一致")
    @Transactional(propagation = Propagation.NEVER)
    void simpleAuditFlowEndToEnd() throws Exception {
        String drafterCode = "00000000";
        String diShiAuditCode = "demo_city";
        String processDefinitionKey = "task_demo_simple_audit";

        // 1. 部署流程
        URL resource = ResourceUtil.getResource("processes/task_demo_simple_audit.bpmn");
        String bpmn = IoUtil.readUtf8(resource.openStream());
        DeploymentDeployVo deployVo = DeploymentDeployVo.builder().name(processDefinitionKey).build();
        deployVo.getResources().add(DeploymentResource.builder().name(processDefinitionKey).text(bpmn).build());
        mockMvc.perform(MockMvcRequestBuilders.post("/workflow/deployment/deploy")
                        .content(JSON.toJSONString(deployVo))
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("0"));

        // 2. 起草人发起流程（自动认领）
        DemoTaskStartVo startVo = DemoTaskStartVo.builder()
                .processDefinitionKey(processDefinitionKey)
                .businessKey("dishi_active_audit")
                .drafterCode(drafterCode)
                .build();
        ApiResponse<String> started = JSONObject.parseObject(mockMvc.perform(
                                MockMvcRequestBuilders.post("/workflow/start")
                                        .content(JSON.toJSONString(startVo))
                                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                        .andExpect(MockMvcResultMatchers.status().isOk())
                        .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("0"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                ApiResponse.class);

        // 3. 起草人查询并提交自己的任务
        String drafterTaskId = firstTaskId(processDefinitionKey, "audit_faqi");
        mockMvc.perform(MockMvcRequestBuilders.post("/workflow/complete")
                        .content(JSON.toJSONString(TaskCompletedVo.tcBuilder()
                                .taskId(drafterTaskId)
                                .assignee(drafterCode)
                                .flowState(FlowState.SUBMIT.code())
                                .data(Map.of("activity_num", 235))
                                .build()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("0"));

        // 4. 地市审核岗-一级：查询 → 认领 → 通过
        String diShiTaskId = firstTaskId(processDefinitionKey, "audit_dishi_01");
        mockMvc.perform(MockMvcRequestBuilders.post("/workflow/claim/do")
                        .content(JSON.toJSONString(TaskClaimVo.builder()
                                .taskId(diShiTaskId)
                                .assignee(diShiAuditCode)
                                .build()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("0"));
        mockMvc.perform(MockMvcRequestBuilders.post("/workflow/complete")
                        .content(JSON.toJSONString(TaskCompletedVo.tcBuilder()
                                .taskId(diShiTaskId)
                                .assignee(diShiAuditCode)
                                .flowState(FlowState.PASS.code())
                                .build()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("0"));

        // 5. 省级审核岗能查到同一流程实例（ID 贯穿一致）
        ApiResponse<List<TaskInfoVo>> shengJiTasks = JSONObject.parseObject(mockMvc.perform(
                                MockMvcRequestBuilders.post("/workflow/search")
                                        .content(JSON.toJSONString(TaskQueryVo.builder()
                                                .processDefinitionKey(processDefinitionKey)
                                                .taskDefinitionKey("audit_shengji")
                                                .build()))
                                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                        .andExpect(MockMvcResultMatchers.status().isOk())
                        .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("0"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                new TypeReference<>() {
                });
        assertThat(shengJiTasks.getData()).as("省级审核岗应查到任务").isNotEmpty();
        assertThat(shengJiTasks.getData().get(0).getProcInsId())
                .as("流程实例 ID 应与发起时一致")
                .isEqualTo(started.getData());
    }

    /** 按流程 key + 任务节点 key 查询第一个任务 ID */
    private String firstTaskId(String processDefinitionKey, String taskDefinitionKey) throws Exception {
        String content = mockMvc.perform(MockMvcRequestBuilders.post("/workflow/search")
                        .content(JSON.toJSONString(TaskQueryVo.builder()
                                .processDefinitionKey(processDefinitionKey)
                                .taskDefinitionKey(taskDefinitionKey)
                                .build()))
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("0"))
                .andDo(MockMvcResultHandlers.print())
                .andReturn()
                .getResponse()
                .getContentAsString();
        ApiResponse<List<TaskInfoVo>> tasks =
                JSONObject.parseObject(content, new TypeReference<>() {
                });
        assertThat(tasks.getData()).as("节点 %s 应有任务", taskDefinitionKey).isNotEmpty();
        return tasks.getData().get(0).getTaskId();
    }
}
