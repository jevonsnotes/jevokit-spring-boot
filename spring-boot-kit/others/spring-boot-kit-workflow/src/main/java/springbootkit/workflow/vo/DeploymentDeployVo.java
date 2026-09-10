package springbootkit.workflow.vo;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
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
public class DeploymentDeployVo extends DeploymentVo {
    @Builder.Default
    private List<DeploymentResource> resources = new ArrayList<>();

}
