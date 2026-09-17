package io.github.jevonsnotes.jevokit.sample.service;

import org.camunda.bpm.engine.*;
import org.springframework.stereotype.Service;
import io.github.jevonsnotes.jevokit.workflow.service.JevokitWorkflowService;

@Service
public class WorkflowService extends JevokitWorkflowService {
    /**
    * 演示服务构造：透传 Camunda 五大引擎服务给父类门面。
    */
    public WorkflowService(RuntimeService runtimeService, TaskService taskService, HistoryService historyService, IdentityService identityService, RepositoryService repositoryService) {
        super(runtimeService, taskService, historyService, identityService, repositoryService);
    }
}
