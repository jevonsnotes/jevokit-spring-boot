package io.github.jevonsnotes.jevokit.workflow.vo;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 部署结果视图：部署 id/时间与资源清单（DeploymentResource），
 * deploy 与 getDeployment 的统一返回形态。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
public class DeploymentInfoVo extends DeploymentVo {
    private String id;
    private Date deploymentTime;
    @Builder.Default
    private List<DeploymentResource> resources = new ArrayList<>();
}
