package io.github.jevonsnotes.jevokit.workflow.vo;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import io.github.jevonsnotes.jevokit.workflow.annotation.FlowVar;

/**
 * @Description 工作流任务对象
 * @Date 2023/5/9 16:38
 * @Version 0.0.1
 */
//@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
public class TaskFallbackVo implements TaskVo{
    private String taskId;
    private String assignee;
}
