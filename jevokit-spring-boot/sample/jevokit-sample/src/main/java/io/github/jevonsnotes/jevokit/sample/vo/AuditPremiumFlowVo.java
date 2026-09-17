package io.github.jevonsnotes.jevokit.sample.vo;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import io.github.jevonsnotes.jevokit.workflow.annotation.FlowVar;
import io.github.jevonsnotes.jevokit.workflow.vo.TaskVo;

import java.math.BigDecimal;

/**
 * @Description TODO
 * @Date 2023/5/11 9:54
 * @Version 0.0.1
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
