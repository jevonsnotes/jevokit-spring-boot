package io.github.jevonsnotes.jevokit.sample.vo;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import io.github.jevonsnotes.jevokit.workflow.annotation.FlowVar;
import io.github.jevonsnotes.jevokit.workflow.vo.TaskVo;

import java.math.BigDecimal;

/**
 * 审计 + 工作流联演示例 VO：实现 TaskVo 家族并以 @FlowVar 声明流程变量，
 * 演示业务对象直接承载流程变量提取。
 */
//@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
public class AuditPremiumFlowVo implements TaskVo {
    @FlowVar
    private BigDecimal premium;
}
