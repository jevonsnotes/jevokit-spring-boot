package io.github.jevonsnotes.jevokit.workflow.vo;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import io.github.jevonsnotes.jevokit.workflow.annotation.FlowVar;

/**
 * @Description TODO
 * @Date 2025/2/27
 * @Version 0.0.1
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
