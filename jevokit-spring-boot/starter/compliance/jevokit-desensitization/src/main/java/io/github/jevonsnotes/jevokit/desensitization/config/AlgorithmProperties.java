package io.github.jevonsnotes.jevokit.desensitization.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstAlgorithm;

/**
 * @Description TODO
 * @Date 2024/11/21
 * @Version 0.0.1
 */
@Data
public class AlgorithmProperties {
    /**
     * 视图脱敏
     */
    private DstAlgorithm view = DstAlgorithm.BASIC;
    /**
     * 日志脱敏
     */
    private DstAlgorithm log = DstAlgorithm.BASIC;
}
