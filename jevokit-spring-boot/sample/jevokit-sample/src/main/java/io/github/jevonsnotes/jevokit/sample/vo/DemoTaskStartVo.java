package io.github.jevonsnotes.jevokit.sample.vo;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import io.github.jevonsnotes.jevokit.workflow.annotation.FlowVar;
import io.github.jevonsnotes.jevokit.workflow.vo.TaskStartVo;

/**
 * 工作流发起示例 VO：扩展 TaskStartVo 增加示例业务字段，演示流程定义 key +
 * 起草人 + 业务主键的最小发起点。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
public class DemoTaskStartVo extends TaskStartVo {
    /**
     * 地市
     */
    @FlowVar(ignoreIfNull = false)
    private String diShiCode;

    /**
     * 省级
     */
    @FlowVar(ignoreIfNull = false)
    private String shengJiCode;

}
