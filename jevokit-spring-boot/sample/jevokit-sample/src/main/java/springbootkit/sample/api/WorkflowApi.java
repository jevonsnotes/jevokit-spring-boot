package springbootkit.sample.api;

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
import springbootkit.core.lang.ApiResponse;
import springbootkit.sample.service.WorkflowService;
import springbootkit.sample.vo.DemoTaskStartVo;
import springbootkit.workflow.vo.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Description TODO
 * @Date 2023/5/9 16:01
 * @Version 0.0.1
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
