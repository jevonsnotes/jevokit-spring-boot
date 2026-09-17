package io.github.jevonsnotes.jevokit.nogen.consts;

 /**
 * 建表探测正则：三种数据库"表不存在"报错文案的模式串（MySQL/PG/H2 各一），
 * 自动建表逻辑据此区分"首次启动建表"与"真实 SQL 错误"——扩库种时必须同步补对应文案。
 */
public class NGConsts {

    public static final String PATTERN_TABLE_NOT_EXISTS = ".*Table.* doesn't exist";
    public static final String PATTERN_TABLE_NOT_EXISTS_PG = "[.\\s\\S]*relation.*does not exist[.\\s\\S]*";
    public static final String PATTERN_TABLE_NOT_EXISTS_H2 = "[.\\s\\S]*Table.*not found[.\\s\\S]*";
}
