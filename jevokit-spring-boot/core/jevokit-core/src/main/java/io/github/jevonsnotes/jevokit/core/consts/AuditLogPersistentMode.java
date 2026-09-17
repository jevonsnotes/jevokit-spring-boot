package io.github.jevonsnotes.jevokit.core.consts;

/**
 * 审计日志持久化模式：
 * <ul>
 *   <li>LOG——仅写日志文件；</li>
 *   <li>DB——仅写数据库表；</li>
 *   <li>MIXED——双写（日志与数据库都落一份）。</li>
 * </ul>
 * 由 jevokit.audit-log.* 配置绑定选择，详见 autoconfigure 侧 AuditLogPersistentProperties。
 */
public enum AuditLogPersistentMode {
    LOG,DB,MIXED
}
