package io.github.jevonsnotes.jevokit.desensitization.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstAlgorithm;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstType;

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
