package io.github.jevonsnotes.jevokit.core.consts;

/**
 * 数据库异常特征正则常量：按数据库方言匹配"表不存在"类 SQL 异常消息，
 * 供自动建表/审计日志自动迁移等组件判定是否需要补建表结构。
 * <p>与 {@link DBType} 一一对应——MySQL（PATTERN_TABLE_NOT_EXISTS）、PostgreSQL、H2 各一条，
 * 匹配语义见各常量：MySQL 为全匹配，PG/H2 为包含匹配。
 */
public class DBConst {
    public static final String PATTERN_TABLE_NOT_EXISTS = ".*Table.* doesn't exist";
    public static final String PATTERN_TABLE_NOT_EXISTS_PG = "[.\\s\\S]*relation.*does not exist[.\\s\\S]*";
    public static final String PATTERN_TABLE_NOT_EXISTS_H2 = "[.\\s\\S]*Table.*not found[.\\s\\S]*";
}
