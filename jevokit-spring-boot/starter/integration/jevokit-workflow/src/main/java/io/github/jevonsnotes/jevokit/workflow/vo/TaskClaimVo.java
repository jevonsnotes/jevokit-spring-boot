package io.github.jevonsnotes.jevokit.workflow.vo;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import io.github.jevonsnotes.jevokit.workflow.annotation.FlowVar;

/**
 * 任务签收入参（taskId + 办理人 assignee）：taskClaim 签收与 taskUndoClaim 撤销签收共用。
 */
//@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
public class TaskClaimVo implements TaskVo{
    private String taskId;
    private String assignee;

}
