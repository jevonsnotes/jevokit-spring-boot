package io.github.jevonsnotes.jevokit.workflow.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.*;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricDetail;
import org.camunda.bpm.engine.history.HistoricVariableUpdate;
import org.camunda.bpm.engine.impl.persistence.entity.DeploymentEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ResourceEntity;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.repository.DeploymentBuilder;
import org.camunda.bpm.engine.repository.Resource;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Attachment;
import org.camunda.bpm.engine.task.Comment;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.task.TaskQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import io.github.jevonsnotes.jevokit.core.lang.CallbackP;
import io.github.jevonsnotes.jevokit.core.lang.CallbackPR;
import io.github.jevonsnotes.jevokit.core.util.CollectionUtil;
import io.github.jevonsnotes.jevokit.workflow.util.FlowUtil;
import io.github.jevonsnotes.jevokit.workflow.vo.*;

import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Description TODO
 * @Date 2023/5/11 9:56
 * @Version 0.0.1
 */
@Data
@Slf4j
@RequiredArgsConstructor
public class JevokitWorkflowService {
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final IdentityService identityService;
    private final RepositoryService repositoryService;
    /**
     * 租户，默认按服务名命名，满足多系统工作流集成共用一个数据库场景
     */
    @Value("${spring.application.name:default}")
    private String tenantId;

    /**
     * 开始流程
     *
     * @param vo
     * @return
     */
    public String taskStart(TaskStartVo vo) {
        Assert.notBlank(vo.getProcessDefinitionKey(), "工作流文件定义不能为空！");
//        Assert.notBlank(vo.getTaskDefinitionKey(), "工作流任务定义不能为空！");
        Assert.notBlank(vo.getDrafterCode(), "起草人不能为空！");
        identityService.setAuthentication(vo.getDrafterCode(), null, CollectionUtil.toList(tenantId));
        ProcessInstance processInstance = runtimeService.createProcessInstanceByKey(vo.getProcessDefinitionKey())
                .businessKey(vo.getBusinessKey())
                .setVariables(FlowUtil.getVarsGlobal(vo))
                .processDefinitionTenantId(tenantId)
                .execute();
//        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(vo.getProcessDefinitionKey(),vo.getBusinessKey(),FlowUtil.getVarsGlobal(vo));  //画流程图时设置的process_id

        log.info("processInstanceId:{}", processInstance.getProcessInstanceId());
        return processInstance.getProcessInstanceId();
    }

    public List<TaskInfoVo> taskQuery(String processDefinitionKey, List<String> taskDefinitionKeys) {
        return taskQuery(null,processDefinitionKey, null, taskDefinitionKeys);
    }
    public List<TaskInfoVo> taskQuery(String processDefinitionKey, String taskDefinitionKey) {
        return taskQuery(null,processDefinitionKey, null, CollectionUtil.toList(taskDefinitionKey));
    }

    public List<TaskInfoVo> taskQuery(String taskId) {
        return taskQuery(null,null, taskId, null);
    }

    public List<TaskInfoVo> taskQuery(String processInstanceId,String processDefinitionKey, String taskId, List<String> taskDefinitionKeys) {
        TaskQuery taskQuery = taskService.createTaskQuery();
        taskQuery.processDefinitionKey(processDefinitionKey)
                .taskDefinitionKeyIn(CollectionUtil.isNotEmpty(taskDefinitionKeys) ? ArrayUtil.toArray(taskDefinitionKeys, String.class) : null)
                .tenantIdIn(tenantId)
                .processInstanceId(processInstanceId);

        if (StrUtil.isNotBlank(taskId)) {
            taskQuery.taskId(taskId);
        }
        return taskQuery.list()
                .stream()
                .map((Function<Task, TaskInfoVo>) task ->
                        TaskInfoVo.builder()
                                .processDefinitionKey(task.getProcessDefinitionId())
                                .taskDefinitionKey(task.getTaskDefinitionKey())
                                .assignee(task.getAssignee())
                                .taskId(task.getId())
                                .procInsId(task.getProcessInstanceId())
                                .startTime(task.getCreateTime())
                                .build())
                .collect(Collectors.toList());
    }

    /**
     * 根据processId获取任务信息
     * @param processId
     * @return
     */
    public List<TaskInfoVo> taskQueryByProcessId(String processId) {
        return taskQuery(processId,null, null, null);
    }

    /**
     * 获取流程历史时间线
     *
     * @param processInstanceId
     * @return
     */
    public List<TaskInfoVo> taskHisTimelines(String processInstanceId) {
        return taskHisTimelines(processInstanceId,TaskInfoVo.class,null);
    }
   /**
     * 获取流程历史时间线
     *
     * @param processInstanceId
     * @return
     */
    public <T extends TaskInfoVo> List<T> taskHisTimelines(String processInstanceId, Class<T> taskClass, CallbackP<T> callback) {
        List<T> tasks = new ArrayList<>();
        HistoryService historyService = getHistoryService();
        List<HistoricActivityInstance> userTasks = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityType("userTask")
                .finished()
                .tenantIdIn(getTenantId())
                .orderByHistoricActivityInstanceEndTime().desc()
                .list();
        Map<String, Comment> comments = taskService.getProcessInstanceComments(processInstanceId).stream().collect(Collectors.toMap(Comment::getTaskId, comment -> comment));
        Map<String, List<TaskAttachmentVo>>  attachmentMap = new HashMap<>();

        taskService.getProcessInstanceAttachments(processInstanceId)
                .forEach(meta ->{
                    List<TaskAttachmentVo> taskAttachmentVos = attachmentMap.computeIfAbsent(meta.getTaskId(), k -> new ArrayList<>());
                    taskAttachmentVos.add(BeanUtil.copyProperties(meta,TaskAttachmentVo.class));
                });

        userTasks.forEach(task -> {
            Map<String, Object> varsLocal = getVarsLocal(task);
            String flowState = (String) varsLocal.get("flowState");
            Comment comment = comments.get(task.getTaskId());
            T vo;
            try {
                vo = taskClass.newInstance();
                vo.setProcInsId(task.getProcessInstanceId())
                        .setProcessDefinitionKey(task.getProcessDefinitionKey())
                        .setTaskDefinitionKey(task.getActivityId())
                        .setTaskDefinitionName(task.getActivityName())
                        .setAssignee(task.getAssignee())
                        .setAssigneeName(task.getAssignee())
                        .setTaskId(task.getId())
                        .setStartTime(task.getStartTime())
                        .setEndTime(task.getEndTime())
                        .setFlowState(flowState)
                        .setComment(comment != null ? comment.getFullMessage() : null)
                        .setCommentTime(comment != null ? comment.getTime() : null)
                        .setAttachments(attachmentMap.get(task.getTaskId()));

                if(callback != null){
                    callback.call(vo);
                }
                tasks.add(vo);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        });

        return tasks;
    }

    /**
     * 完成流程（注意 complete 方法保存的变量没有taskId）
     *
     * @param vo
     */
    @Transactional
    public <T> void taskComplete(TaskCompletedVo<T> vo) {
        Task task = taskService.createTaskQuery()
                .taskId(vo.getTaskId())
                .tenantIdIn(tenantId)
                .taskAssignee(vo.getAssignee())
                .singleResult();
        Assert.notNull(task, "流程不存在！");
        taskService.complete(vo.getTaskId(), operationWorkflow(vo));
    }

    /**
     * 完成流程（注意 complete 方法保存的变量没有taskId）
     *
     * @param vo
     */
    @Transactional
    public <T> void taskComplete(TaskCompletedCommentVo<T> vo) {
        Task task = taskService.createTaskQuery()
                .taskId(vo.getTaskId())
                .tenantIdIn(tenantId)
                .taskAssignee(vo.getAssignee())
                .singleResult();
        Assert.notNull(task, "流程不存在！");
        taskService.createComment(vo.getTaskId(), task.getProcessInstanceId(), vo.getComment());
        taskService.complete(vo.getTaskId(), operationWorkflow(vo));
    }


    /**
     * 完成委派流程
     *
     * @param vo
     */
    public <T> void taskResolve(TaskCompletedVo<T> vo) {
        taskService.resolveTask(vo.getTaskId(), operationWorkflow(vo));
    }

    /**
     * 添加评论
     *
     * @param vo
     */
    public void taskComment(TaskCommentVo vo) {
        Task task = taskService.createTaskQuery()
                .taskId(vo.getTaskId())
                .tenantIdIn(tenantId)
                .processInstanceId(vo.getProcInsId())
                .singleResult();
        Assert.notNull(task, "流程节点不存在！");
        Comment comment = taskService.createComment(vo.getTaskId(), vo.getProcInsId(), vo.getComment());
        vo.setId(comment.getId());
        vo.setCreateTime(comment.getTime());
    }

    /**
     * 添加附件地址（不存储文件流）
     * @param taskId
     * @param vos
     */
    public TaskAttachmentChangeVo taskAttachment(String taskId,List<TaskAttachmentVo> vos) {
        TaskAttachmentChangeVo res = TaskAttachmentChangeVo.builder().build();
        if(CollectionUtil.isNotEmpty(vos)){
            Map<String, TaskAttachmentVo> old = vos.stream().filter((vo) -> StrUtil.isNotBlank(vo.getId())).collect(Collectors.toMap(TaskAttachmentVo::getId, vo -> vo));
            List<TaskAttachmentVo> add = vos.stream().filter((vo) -> StrUtil.isBlank(vo.getId())).collect(Collectors.toList());

            List<Attachment> del = taskService.getTaskAttachments(taskId).stream().filter((po) -> !old.containsKey(po.getId())).collect(Collectors.toList());

            del.forEach((meta)-> taskService.deleteTaskAttachment(taskId,meta.getId()));
            add.forEach((meta)-> taskService.createAttachment(meta.getType(),taskId,meta.getProcessInstanceId(),meta.getName(),meta.getDescription(),meta.getUrl()));
            res.setNoChanges(new ArrayList<>(old.values()));
            res.setDeletes(del.stream().map((meta)->BeanUtil.copyProperties(meta,TaskAttachmentVo.class)).collect(Collectors.toList()));
            res.setAdds(add);
        }
        return res;
    }

    /**
     * 认领
     *
     * @param vo
     */
    public void taskClaim(TaskClaimVo vo) {
        taskService.claim(vo.getTaskId(), vo.getAssignee());
    }

    /**
     * 取消认领
     *
     * @param vo
     */
    public void taskUndoClaim(TaskClaimVo vo) {
        Assert.notBlank(vo.getAssignee(), "当前受理人不能为空！");
        Optional.ofNullable(taskService.createTaskQuery()
                        .taskId(vo.getTaskId())
                        .tenantIdIn(tenantId)
                        .taskAssignee(vo.getAssignee())
                        .singleResult())
                .orElseThrow(() -> new RuntimeException("任务不存在或无权限"));
        taskService.claim(vo.getTaskId(), null);
    }

    /**
     * 回退到上一层
     *
     * @param vo
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void taskFallbackLast(TaskFallbackVo vo) {
        Task task = taskService.createTaskQuery()
                .taskId(vo.getTaskId())
                .tenantIdIn(tenantId)
                .taskAssigneeIn(vo.getAssignee())
                .singleResult();
        Assert.notNull(task, "当前任务无法回退");
        List<HistoricActivityInstance> userTask = getHisActInstanceByUserTaskForFallBack(task);
        //上一节点
        fallbackToHisActInstance(task, userTask.get(userTask.size() - 1));
    }

    /**
     * 回退到起草人
     *
     * @param vo
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void taskFallbackFirst(TaskFallbackVo vo) {
        Task task = taskService.createTaskQuery().taskId(vo.getTaskId()).taskAssigneeIn(vo.getAssignee()).singleResult();
        Assert.notNull(task, "任务不存在或无权限，回退失败！");
        List<HistoricActivityInstance> userTask = getHisActInstanceByUserTaskForFallBack(task);
        //初始节点
        fallbackToHisActInstance(task, userTask.get(0));

    }


    /**
     * 部署工作流
     * 支持classpath路径方式、字符串方式部署
     *
     * @param vo 部署对象
     * @return 返回部署成功信息
     */
    public DeploymentInfoVo deploy(DeploymentDeployVo vo) {
        Assert.notBlank(vo.getName(), "部署名称不能为空！");
        Assert.isTrue(!vo.getResources().isEmpty(), "资源不能为空！");
        DeploymentBuilder deploymentBuilder = repositoryService
                .createDeployment()
                .name(vo.getName())
                .tenantId(tenantId);
        for (DeploymentResource resource : vo.getResources()) {
            // 资源名称和路径不为空时，走输入流部署方式
            if (StrUtil.isNotBlank(resource.getName()) && StrUtil.isNotBlank(resource.getPath())) {
                ClassPathResource classPathResource = new ClassPathResource(resource.getPath());
                InputStream stream = classPathResource.getStream();
                try {
                    deploymentBuilder.addInputStream(rebuildResourceName(resource.getName()), stream);
                } finally {
                    IoUtil.close(stream);
                }
            }
            // 资源路径不为空时，走文件部署方式
            else if (StrUtil.isNotBlank(resource.getPath())) {
                deploymentBuilder.addClasspathResource(resource.getPath());
            }
            // 资源内容不为空时，走字符串部署方式
            else if (StrUtil.isNotBlank(resource.getText())) {
                deploymentBuilder.addString(rebuildResourceName(resource.getName()), resource.getText());
            } else {
                throw new RuntimeException("不支持的资源类型！");
            }
        }
        // 部署
        Deployment deploy = deploymentBuilder.deploy();
        // 封装部署成功信息
        return buildDeploymentInfo(deploy);
    }

    /**
     * 回退到某个部署版本
     *
     * @param vo 历史部署信息
     * @return 返回部署信息
     */
    public DeploymentInfoVo deployRollback(DeploymentDeployRollbackVo vo) {
        Assert.notBlank(vo.getName(), "部署名称不能为空！");
        Assert.notBlank(vo.getDeploymentId(), "部署ID不能为空！");
        DeploymentBuilder deploymentBuilder = repositoryService
                .createDeployment()
                .tenantId(tenantId)
                .name(vo.getName());
        if (CollectionUtil.isNotEmpty(vo.getResourceIds())) {
            deploymentBuilder.addDeploymentResourcesById(vo.getDeploymentId(), vo.getResourceIds());
        } else {
            deploymentBuilder.addDeploymentResources(vo.getDeploymentId());
        }
        Deployment deploy = deploymentBuilder.deploy();
        // 封装部署成功信息
        return buildDeploymentInfo(deploy);
    }

    public List<DeploymentInfoVo> getDeployment(String deploymentName) {
        List<DeploymentInfoVo> infoVos = new ArrayList<>();
        List<Deployment> deployments = repositoryService.createDeploymentQuery()
                .tenantIdIn(tenantId)
                .deploymentName(deploymentName)
                .list();
        deployments.forEach(deployment -> {
            infoVos.add(buildDeploymentInfo(deployment));
        });
        return infoVos;
    }

    public void deleteDeployment(String deploymentNameId) {
        repositoryService.deleteDeployment(deploymentNameId);
    }

    private DeploymentInfoVo buildDeploymentInfo(Deployment deployment) {
        // 封装部署信息
        DeploymentInfoVo deploymentInfoVo = DeploymentInfoVo.builder()
                .id(deployment.getId())
                .name(deployment.getName())
                .deploymentTime(deployment.getDeploymentTime())
                .resources(new ArrayList<>())
                .build();
        if (deployment instanceof DeploymentEntity) {
            DeploymentEntity entity = ((DeploymentEntity) deployment);
            // fix lazy load resource cause null pointer exception problem
            if (canLazyLoadResources(entity)) {
                for (Map.Entry<String, ResourceEntity> entry : entity.getResources().entrySet()) {
                    ResourceEntity value = entry.getValue();
                    deploymentInfoVo.getResources()
                            .add(DeploymentResource.builder()
                                    .id(value.getId())
                                    .deploymentId(entity.getId())
                                    .text(new String(value.getBytes()))
                                    .name(value.getName())
                                    .build());
                }
            }

        }
        return deploymentInfoVo;
    }

    private boolean canLazyLoadResources(DeploymentEntity deployment) {
        Map<String, ResourceEntity> resources;
        try {
            resources = deployment.getResources();
        } catch (Exception e) {
            // 这里deployment.getResources()含懒加载方法,没有上下文会报空指针
            log.debug(e.getMessage(), e);
            resources = repositoryService.getDeploymentResources(deployment.getId())
                    .stream()
                    .collect(Collectors.toMap(Resource::getName, resource -> (ResourceEntity) resource));
            deployment.setResources(resources);
        }
        return resources != null;
    }

    private String rebuildResourceName(String resourceName) {
        return StrUtil.isBlank(resourceName) || resourceName.endsWith(".bpmn") ? resourceName : resourceName + ".bpmn";
    }

    private <T> Map<String, Object> operationWorkflow(TaskCompletedVo<T> vo) {
        Map<String, Object> variables = taskService.getVariables(vo.getTaskId());
        log.info(JSONObject.toJSONString(variables));
        variables.putAll(FlowUtil.getVarsGlobal(vo));
        taskService.setVariablesLocal(vo.getTaskId(), FlowUtil.getVarsLocal(vo));
        return variables;
    }

    private List<HistoricActivityInstance> getHisActInstanceByUserTaskForFallBack(Task task) {


        HistoryService historyService = SpringUtil.getBean(HistoryService.class);
        List<HistoricActivityInstance> userTask = historyService
                .createHistoricActivityInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .activityType("userTask").finished()
                .tenantIdIn(tenantId)
                .orderByHistoricActivityInstanceEndTime()
                .asc()
                .list();

        Assert.isTrue(!userTask.isEmpty(), "当前任务无法回退");
        Assert.isFalse(userTask.get(0).getActivityId().equals(task.getTaskDefinitionKey()), "初始节点无法回退");
        return userTask;
    }

    private void fallbackToHisActInstance(Task currentTask, HistoricActivityInstance hisActInstance) {
        runtimeService.createProcessInstanceModification(currentTask.getProcessInstanceId())
                .cancelAllForActivity(currentTask.getTaskDefinitionKey())
                .setAnnotation("Annotation：回退")
                .startBeforeActivity(hisActInstance.getActivityId())
                //                .setVariables(getVars(historicActivityInstance))
                .setVariablesLocal(getVarsLocal(hisActInstance))
                .execute();
        // 获取重新生成的上级任务
        Task newLastTask = taskService.createTaskQuery()
                .processInstanceId(hisActInstance.getProcessInstanceId())
                .taskDefinitionKey(hisActInstance.getActivityId())
                .tenantIdIn(tenantId)
                .singleResult();
        // 自动设置认领人
        taskClaim(TaskClaimVo.builder()
                .taskId(newLastTask.getId())
                .assignee(hisActInstance.getAssignee())
                .build());
    }

    /**
     * 获取本地最终变量
     *
     * @param instance
     * @return
     */
    private Map<String, Object> getVarsLocal(HistoricActivityInstance instance) {
        Map<String, Object> variables = new HashMap<>();

        List<HistoricDetail> list = historyService.createHistoricDetailQuery()
                .activityInstanceId(instance.getId())
                .tenantIdIn(tenantId)
                .orderByTime()
                .desc()
                .list();

        list.forEach(ele -> {
            if (ele instanceof HistoricVariableUpdate) {
                HistoricVariableUpdate update = (HistoricVariableUpdate) ele;
                if (!variables.containsKey(update.getVariableName())) {
                    variables.put(update.getVariableName(), update.getValue());
                }
            }
        });
        return variables;
    }

}
