package io.github.jevonsnotes.jevokit.audit.log.dto;

import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.Date;

/**
 * 审计事件线程上下文 DTO：一次请求链路的审计事件骨架——三级键（globalKey 会话级 /
 * parentKey 父事件 / localKey 本事件）与起止时间，支持嵌套（forward/异步派生）事件的父子关联。
 * 使用方式：由 AuditLogUtils 以 ThreadLocal 维护（preHandle 入栈、afterHandle 回退到 parent），
 * 业务不直接操作；globalKey 同一入口请求全程不变，localKey 每次进入生成新值。
 * <p>关键约束：parent 引用构成栈式链，{@code AuditLogUtils.clear()} 逐层回退——
 * 泄漏调用会导致 ThreadLocal 残留并串到线程池的下一个请求。
 */
@Data
@SuperBuilder
public class AuditLogEventDto {
    private Date startTime;
    private Date endTime;
    private String localKey;
    private String globalKey;
    private String parentKey;
    private AuditLogEventDto parent;
}
