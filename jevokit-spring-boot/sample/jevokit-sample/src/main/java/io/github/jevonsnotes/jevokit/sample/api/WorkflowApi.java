package io.github.jevonsnotes.jevokit.sample.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.task.Task;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.github.jevonsnotes.jevokit.core.lang.ApiResponse;
import io.github.jevonsnotes.jevokit.sample.service.WorkflowService;
import io.github.jevonsnotes.jevokit.sample.vo.DemoTaskStartVo;
import io.github.jevonsnotes.jevokit.workflow.vo.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工作流演示端点（/workflow/*）：发起/查询/办结全链路，E2E 用 H2 内嵌 Camunda
 * 验证流程实例状态流转。
 */
@Slf4j
@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class WorkflowApi {
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final WorkflowService workflowService;
    private final AuthorizationService authorizationService;



    @RequestMapping("/start")
/**
    * 发起流程演示：@FlowVo 扩展点 + 租户隔离下的 taskStart。
    * @param vo 发起参数
    * @return 流程实例 id
    */
    public ApiResponse<String> start(@RequestBody DemoTaskStartVo vo) {
        try {

            String info = workflowService.taskStart(vo);
            return ApiResponse.ok(info);
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    @RequestMapping("/search")
/**
    * 任务查询演示：四维过滤组合查当前任务。
    * @param vo 查询条件
    * @return 任务快照列表
    */
    public ApiResponse<List<TaskVo>> search(@RequestBody TaskQueryVo vo) {
        List<TaskVo> collect = taskService
                .createTaskQuery()
                .processDefinitionKey(vo.getProcessDefinitionKey())
                .taskDefinitionKey(vo.getTaskDefinitionKey())
                .list()
                .stream()
                .map((Function<Task, TaskVo>) task ->
                        TaskInfoVo.builder()
                                .processDefinitionKey(task.getProcessDefinitionId())
                                .taskDefinitionKey(task.getTaskDefinitionKey())
                                .assignee(task.getAssignee())
                                .taskId(task.getId())
                                .procInsId(task.getProcessInstanceId())
                                .build())
                .collect(Collectors.toList());
        return ApiResponse.ok(collect);
    }
    /**
     * 认领
     * @param vo
     * @return
     */
    @RequestMapping("/claim/do")
/**
    * 签收演示。
    * @param vo taskId + assignee
    * @return 操作结果
    */
    public ApiResponse<String> doClaim(@RequestBody TaskClaimVo vo){
        workflowService.taskClaim(vo);
//        Map<String, Object> variablesLocal = taskService.getVariablesLocal(vo.getTaskId());
//        variablesLocal.put("assignee",vo.getAssignee());
//        taskService.setVariablesLocal(vo.getTaskId(),variablesLocal);

        return ApiResponse.ok();
    }

    /**
     * 取消认领
     * @param vo
     * @return
     */
    @RequestMapping("/claim/undo")
/**
    * 撤销签收演示（含签收人一致性校验）。
    * @param vo taskId + 当前 assignee
    * @return 操作结果
    */
    public ApiResponse<String> undoClaim(@RequestBody TaskClaimVo vo){
        workflowService.taskUndoClaim(vo);
        return ApiResponse.ok();
    }
    /**
     * 完成节点
     * @param vo
     * @return
     */
    @RequestMapping("/complete")
/**
    * 办结演示：flowState 驱动流转 + 泛型业务数据随办结写入。
    * @param vo 办结参数
    * @return 操作结果
    */
    public ApiResponse<String> complete(@RequestBody TaskCompletedVo<Map<String,Object>> vo){
        try {
            workflowService.taskComplete(vo);
            return  ApiResponse.ok("success");
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            return ApiResponse.fail(e.getMessage());
        }
    }

    /**
     * 添加意见
     * @param vo
     * @return
     */
    @RequestMapping("/comment")
/**
    * 审批意见演示：落库后回填 id/createTime。
    * @param vo 意见参数
    * @return 回填后的意见参数
    */
    public ApiResponse<TaskCommentVo> comment(@RequestBody TaskCommentVo vo){
        try {
            workflowService.taskComment(vo);
            return ApiResponse.ok(vo);
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            return ApiResponse.fail(e.getMessage());
        }
    }


    /**
     * 退回
     * @param vo
     * @return
     */
    @RequestMapping("/fallback/first")
/**
    * 回退演示：按 FlowState 语义回上一级或起草人。
    * @param vo taskId + assignee
    * @return 操作结果
    */
    public ApiResponse<String> fallback(@RequestBody TaskFallbackVo vo){
        try {
            workflowService.taskFallbackFirst(vo);
        } catch (IllegalArgumentException e) {
            log.error(e.getMessage(),e);
            return ApiResponse.fail(e.getMessage());
        }

        return ApiResponse.ok();
    }
    /**
     * 退回
     * @param vo
     * @return
     */
    @RequestMapping("/fallback/last")
/**
    * 回退上一级演示。
    * @param vo taskId + assignee
    * @return 操作结果
    */
    public ApiResponse<String> fallbackLast(@RequestBody TaskFallbackVo vo){
        try {
            workflowService.taskFallbackLast(vo);

        } catch (IllegalArgumentException e) {
            log.error(e.getMessage(),e);
            return ApiResponse.fail(e.getMessage());
        }

        return ApiResponse.ok();
    }

    private Map<String,Object> getVars(HistoricActivityInstance instance){
        Map<String, Object> variables = new HashMap<>();
        variables.put("userCode",instance.getAssignee());
        return variables;
    }

    /**
     * 获取部署清单
     * @return
     */
    @RequestMapping("/deployment/get")
/**
    * 部署版本查询演示。
    * @param deploymentName 部署名
    * @return 部署列表
    */
    public ApiResponse<List<DeploymentInfoVo>> getDeployment(@RequestParam("deploymentName") String deploymentName){
        try {
            List<DeploymentInfoVo> deployment = workflowService.getDeployment(deploymentName);
            return ApiResponse.ok(deployment);
        } catch (IllegalArgumentException e) {
            log.error(e.getMessage(),e);
            return ApiResponse.fail(e.getMessage());
        }
    }
    /**
     * 获取部署清单
     * @return
     */
    @RequestMapping("/deployment/delete")
/**
    * 删除部署演示（级联删实例与历史）。
    * @param deploymentId 部署 id
    * @return 操作结果
    */
    public ApiResponse<String> deleteDeployment(@RequestParam("deploymentId") String deploymentId){
        try {
            workflowService.deleteDeployment(deploymentId);
            return ApiResponse.ok();
        } catch (IllegalArgumentException e) {
            log.error(e.getMessage(),e);
            return ApiResponse.fail(e.getMessage());
        }
    }
    /**
     * 部署工作流
     * @return
     */
    @RequestMapping("/deployment/deploy")
/**
    * 部署演示：classpath/字符串资源三形态。
    * @param vo 部署参数
    * @return 部署结果
    */
    public ApiResponse<DeploymentInfoVo> deployDeployment(@RequestBody DeploymentDeployVo vo){
        try {
            DeploymentInfoVo deployment = workflowService.deploy(vo);
            return ApiResponse.ok(deployment);
        } catch (IllegalArgumentException e) {
            log.error(e.getMessage(),e);
            return ApiResponse.fail(e.getMessage());
        }
    }
    /**
     * 部署工作流测试
     * @return
     */
    @RequestMapping("/deployment/deploy/rollback")
/**
    * 部署回滚演示。
    * @param vo 回滚参数
    * @return 新部署结果
    */
    public ApiResponse<DeploymentInfoVo> deployRollbackDeployment(@RequestBody DeploymentDeployRollbackVo vo){
        try {
            DeploymentInfoVo infoVo = workflowService.deployRollback(vo);
            return ApiResponse.ok(infoVo);
        } catch (IllegalArgumentException e) {
            log.error(e.getMessage(),e);
            return ApiResponse.fail(e.getMessage());
        }
    }

}
