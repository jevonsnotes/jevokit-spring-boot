package springbootkit.workflow.vo;

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
public class DeploymentResource {
    /**
     * 资源id(部署后自动生成)
     */
    private String id;
    /**
     * 部署id(部署后自动生成)
     */
    private String deploymentId;
    /**
     * 资源名称，文件流、字符串部署方式必填
     */
    private String name;
    /**
     * 字符串部署必填
     */
    private String text;
    /**
     * 文件、文件流部署必填
     */
    private String path;
}
