package io.github.jevonsnotes.jevokit.workflow.vo;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.Date;
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
public class DeploymentInfoVo extends DeploymentVo {
    private String id;
    private Date deploymentTime;
    @Builder.Default
    private List<DeploymentResource> resources = new ArrayList<>();
}
