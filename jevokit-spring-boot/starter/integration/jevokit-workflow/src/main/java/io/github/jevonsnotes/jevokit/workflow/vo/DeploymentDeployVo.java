package io.github.jevonsnotes.jevokit.workflow.vo;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 部署流程入参：在基座 name 之上携带 BPMN 资源清单（DeploymentResource 列表），
 * 供 deploy 以非缺省资源集部署。
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
