package io.github.jevonsnotes.jevokit.sample.service;

import org.camunda.bpm.engine.*;
import org.springframework.stereotype.Service;
import io.github.jevonsnotes.jevokit.workflow.service.JevokitWorkflowService;

/**
 * @Description
 * @Date 2025/4/29
 * @Version 0.0.1
 */
@Service
public class WorkflowService extends JevokitWorkflowService {
    public WorkflowService(RuntimeService runtimeService, TaskService taskService, HistoryService historyService, IdentityService identityService, RepositoryService repositoryService) {
        super(runtimeService, taskService, historyService, identityService, repositoryService);
    }
}
