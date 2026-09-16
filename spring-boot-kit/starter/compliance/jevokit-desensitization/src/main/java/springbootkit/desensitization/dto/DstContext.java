package springbootkit.desensitization.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import springbootkit.desensitization.consts.DstAlgorithm;
import springbootkit.desensitization.consts.DstLevel;
import springbootkit.desensitization.consts.DstType;

/**
 * @Description TODO
 * @Date 2024/11/27
 * @Version 0.0.1
 */
@Data
@SuperBuilder
@Accessors(chain = true)
public class DstContext {
    private DstType type;
    private DstLevel level;
//    private DstAlgorithm algorithm;
}
