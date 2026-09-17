package io.github.jevonsnotes.jevokit.core.consts;

/**
 * @Description TODO
 * @Date 2024/6/14 11:30
 * @Version 0.0.1
 */
public class DBConst {
    public static final String PATTERN_TABLE_NOT_EXISTS = ".*Table.* doesn't exist";
    public static final String PATTERN_TABLE_NOT_EXISTS_PG = "[.\\s\\S]*relation.*does not exist[.\\s\\S]*";
    public static final String PATTERN_TABLE_NOT_EXISTS_H2 = "[.\\s\\S]*Table.*not found[.\\s\\S]*";
}
