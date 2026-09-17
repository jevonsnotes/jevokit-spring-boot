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
 * Camunda 7 工作流门面服务：以 VO 约定封装流程的发起 / 查询 / 签收 / 办结 / 回退 / 意见 /
 * 附件 / 部署 / 回滚全生命周期，业务侧注入本 Bean 即可，无需直接触碰 Camunda 引擎 API。
 *
 * <p>用法：taskStart 发起（@FlowVar 字段经 FlowUtil 自动提为流程变量）；taskQuery 族按
 * 节点/任务/实例维度查询；taskClaim/taskUndoClaim/taskCompleted/taskFallback* 处理办理动作；
 * deploy/deployRollback/deleteDeployment 管理流程定义。
 *
 * <p>关键约束：
 * <ul>
 *   <li>多系统共库隔离靠 tenantId（默认取 spring.application.name）——同库多系统必须保证
 *       应用名唯一，否则流程定义与实例互相可见；</li>
 *   <li>taskStart 以起草人身份 setAuthentication 后启动，操作者身份进 Camunda 历史表；
 *       写类方法（办结/回退族）带 @Transactional(rollbackFor=Exception.class)，与业务事务同生共死。</li>
 * </ul>
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
    * 发起流程实例：以起草人身份鉴权后按流程定义 key 启动，@FlowVar 全局变量随启动写入。
    * @param vo 发起参数（processDefinitionKey 须与 BPMN process id 一致，起草人不可空）
    * @return 流程实例 id（processInstanceId）
    * @throws cn.hutool.core.lang.AssertException 流程定义或起草人缺失时前置校验失败
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

/**
    * 查当前任务（流程定义 + 节点定义集合维度）。语义同全参形态。
    * @param processDefinitionKey 流程定义 key，可空
    * @param taskDefinitionKeys 节点定义 key 集合，可空
    * @return 当前任务快照列表（本租户内），无命中返回空列表
    */
    public List<TaskInfoVo> taskQuery(String processDefinitionKey, List<String> taskDefinitionKeys) {
        return taskQuery(null,processDefinitionKey, null, taskDefinitionKeys);
    }
/**
    * 查当前任务（流程定义 + 单节点维度）。
    * @param processDefinitionKey 流程定义 key，可空
    * @param taskDefinitionKey 节点定义 key，可空
    * @return 当前任务快照列表
    */
    public List<TaskInfoVo> taskQuery(String processDefinitionKey, String taskDefinitionKey) {
        return taskQuery(null,processDefinitionKey, null, CollectionUtil.toList(taskDefinitionKey));
    }

/**
    * 按任务 id 精确查当前任务。
    * @param taskId 任务 id
    * @return 命中的任务快照（0 或 1 条）
    */
    public List<TaskInfoVo> taskQuery(String taskId) {
        return taskQuery(null,null, taskId, null);
    }

/**
    * 查当前任务全参形态：四个维度 AND 组合，全部施加本租户过滤。
    * @param processInstanceId 流程实例 id，可空
    * @param processDefinitionKey 流程定义 key，可空
    * @param taskId 任务 id，非空时精确匹配
    * @param taskDefinitionKeys 节点定义 key 集合，可空
    * @return 当前任务快照列表（注意 processDefinitionKey 回填的是引擎侧完整定义 id）
    */
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
    * 按流程实例 id 查其全部当前任务。
    * @param processId 流程实例 id
    * @return 该实例的当前任务列表
    */
    public List<TaskInfoVo> taskQueryByProcessId(String processId) {
        return taskQuery(processId,null, null, null);
    }

/**
    * 流程历史时间线（默认 TaskInfoVo 形态）：已完成 userTask 按结束时间倒序，附意见/附件/局部变量。
    * @param processInstanceId 流程实例 id
    * @return 历史任务时间线，无历史返回空列表
    */
    public List<TaskInfoVo> taskHisTimelines(String processInstanceId) {
        return taskHisTimelines(processInstanceId,TaskInfoVo.class,null);
    }
/**
    * 历史时间线全参形态：自定义返回 VO 类型（须有无参构造）+ 每条回读回调。
    * @param processInstanceId 流程实例 id
    * @param taskClass 返回 VO 类型（newInstance 反射构造）
    * @param callback 每条时间线装填后的回调（补业务字段挂载点），可空
    * @return 历史任务时间线
    * @throws RuntimeException VO 反射构造失败时包装抛出
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
/**
    * 办结当前任务：校验任务在且办理人匹配后 complete，FlowState 语义驱动流转。
    * @param vo 办结参数（taskId + assignee + flowState + 泛型业务数据）
    * @throws cn.hutool.core.lang.AssertException 任务不存在或办理人不匹配
    */
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
/**
    * 带意见办结：先落审批意见再 complete（意见与办结同事务）。
    * @param vo 办结参数（含 comment 意见内容）
    * @throws cn.hutool.core.lang.AssertException 任务不存在或办理人不匹配
    */
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
    * 委派归还：被委派人处理完毕后 resolve 回委派人（与 complete 的区别见 Camunda 委派语义）。
    * @param vo 办结参数（taskId + 流转变量）
    */
    public <T> void taskResolve(TaskCompletedVo<T> vo) {
        taskService.resolveTask(vo.getTaskId(), operationWorkflow(vo));
    }

/**
    * 添加审批意见（不办结任务）：落库后回填 vo 的 id/createTime 供调用方回显。
    * @param vo 意见参数（taskId/procInsId 定位 + comment 内容）
    * @throws cn.hutool.core.lang.AssertException 目标任务不存在
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
    * 附件同步：以目标清单与引擎现存附件 diff，执行增删并返回三桶结果。空清单直接返回空壳（不删任何现存附件）。
    * @param taskId 目标任务 id
    * @param vos 目标附件清单（带 id 视为保留，无 id 视为新增）
    * @return deletes/adds/noChanges 三桶（引擎现存但清单未含的进 deletes 并被删除）
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
    * 签收任务（claim 到指定办理人，抢占语义）。
    * @param vo taskId + assignee
    */
    public void taskClaim(TaskClaimVo vo) {
        taskService.claim(vo.getTaskId(), vo.getAssignee());
    }

/**
    * 撤销签收：先校验当前签收人一致再释放（防越权释放他人签收）。
    * @param vo taskId + 当前 assignee
    * @throws RuntimeException 任务不存在或签收人不匹配
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
/**
    * 回退到上一节点（按历史 userTask 序列取最近一个），事务内执行。
    * @param vo taskId + assignee
    * @throws cn.hutool.core.lang.AssertException 任务不存在或无历史可回退
    */
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
/**
    * 回退到起草节点（历史 userTask 序列第一个），事务内执行。
    * @param vo taskId + assignee
    * @throws cn.hutool.core.lang.AssertException 任务不存在或无历史可回退
    */
    public void taskFallbackFirst(TaskFallbackVo vo) {
        Task task = taskService.createTaskQuery().taskId(vo.getTaskId()).taskAssigneeIn(vo.getAssignee()).singleResult();
        Assert.notNull(task, "任务不存在或无权限，回退失败！");
        List<HistoricActivityInstance> userTask = getHisActInstanceByUserTaskForFallBack(task);
        //初始节点
        fallbackToHisActInstance(task, userTask.get(0));

    }


/**
    * 部署流程定义：资源三选一形态——name+path 走 classpath 输入流、仅 path 走 classpath 直载、
    * text 走字符串部署；全空抛不支持异常。
    * @param vo 部署名 + 资源清单（至少一条）
    * @return 部署结果视图（部署 id/时间/资源）
    * @throws cn.hutool.core.lang.AssertException 名称或资源为空
    * @throws RuntimeException 资源形态不被支持
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
    * 部署回滚：以既有部署的资源重建新部署版本（引擎不删旧版，靠版本序回退）。
    * @param vo 部署名 + 目标 deploymentId + 可选 resourceIds 子集（空则整包复制）
    * @return 新部署结果视图
    * @throws cn.hutool.core.lang.AssertException 名称或部署 id 为空
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

/**
    * 按部署名查本租户全部部署版本。
    * @param deploymentName 部署名
    * @return 部署视图列表（含资源清单）
    */
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

/**
    * 删除部署（引擎默认级联删除关联流程实例与历史——删前自行确认）。
    * @param deploymentNameId 部署 id
    */
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
