package io.github.jevonsnotes.jevokit.workflow.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 部署回滚入参：deploymentId 定位目标部署，resourceIds 指定回滚涉及的资源子集，
 * 供 deployRollback 执行版本回退。
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
