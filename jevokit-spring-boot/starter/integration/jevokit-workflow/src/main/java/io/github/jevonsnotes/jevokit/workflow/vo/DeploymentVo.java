package io.github.jevonsnotes.jevokit.workflow.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

/**
 * 部署定义基座：name 为流程定义名，是 deploy/getDeployment/deleteDeployment
 * 定位与命名基准。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
public class DeploymentVo {
    /**
     * 部署名称
     */
    private String name;

}
