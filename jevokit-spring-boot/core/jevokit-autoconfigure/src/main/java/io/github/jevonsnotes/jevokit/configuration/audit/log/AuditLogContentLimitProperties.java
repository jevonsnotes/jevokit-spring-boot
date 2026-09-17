package io.github.jevonsnotes.jevokit.configuration.audit.log;

import lombok.Data;
import io.github.jevonsnotes.jevokit.core.consts.HttpMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计内容长度限制配置（spkit.audit.log.content-limit.*）：
 * queryString/requestBody/responseBody 分别约束三类报文的最大记录长度，-1 表示不限制（默认）；
 * 超限行为由 strategy 决定——TRUNC 截断保留前 N 字符（默认），ABORT 放弃记录该段内容。
 */
@Data
public class AuditLogContentLimitProperties {
    /** query string 最大记录长度，-1 不限制 */
    private Integer queryString = -1;
    /** 请求体最大记录长度，-1 不限制 */
    private Integer requestBody = -1;
    /** 响应体最大记录长度，-1 不限制 */
    private Integer responseBody = -1;
    /** 超限策略：TRUNC 截断 / ABORT 弃录 */
    private Strategy strategy = Strategy.TRUNC;

    /** 超限处理策略枚举 */
    public enum Strategy {
        TRUNC,ABORT
    }

}
