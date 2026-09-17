package io.github.jevonsnotes.jevokit.desensitization.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstAlgorithm;

/**
 * 分链路脱敏算法配置（spkit.desensitization.algorithm）：view 管接口出参/视图渲染，
 * log 管日志输出（logback appender 与 LogBuilder），两条链路互不影响。
 * 使用方式：如视图用 BASIC 掩码、日志用 SM4 密文（日志中可凭权限还原排查问题）；
 * 取值与语义见 {@link DstAlgorithm}。
 * <p>关键约束：log 链路常年在异步线程执行，选 SM4/MIXED 前须确认 spkit.crypto.sm4-key 全局可用。
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
