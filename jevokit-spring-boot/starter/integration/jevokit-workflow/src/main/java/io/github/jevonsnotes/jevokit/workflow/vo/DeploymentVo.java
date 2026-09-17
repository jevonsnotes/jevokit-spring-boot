package io.github.jevonsnotes.jevokit.workflow.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

/**
 * @Description TODO
 * @Date 2025/3/4
 * @Version 0.0.1
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
