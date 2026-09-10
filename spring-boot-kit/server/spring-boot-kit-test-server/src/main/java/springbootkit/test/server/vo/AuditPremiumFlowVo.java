package springbootkit.test.server.vo;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import springbootkit.workflow.annotation.FlowVar;
import springbootkit.workflow.vo.TaskVo;

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
