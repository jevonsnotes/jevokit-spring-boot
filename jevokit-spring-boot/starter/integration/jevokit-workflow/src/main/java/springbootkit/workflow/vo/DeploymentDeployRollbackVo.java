package springbootkit.workflow.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @Description TODO
 * @Date 2025/3/4
 * @Version 0.0.1
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
public class DeploymentDeployRollbackVo extends DeploymentVo {
    /**
     * 历史部署id，必填
     */
    private String deploymentId;
    /**
     * 历史资源id,选填
     */
    private List<String> resourceIds;

}
