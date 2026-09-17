package io.github.jevonsnotes.jevokit.configuration.security.sql.inject;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 注入默认污染特征正则表：刻意保守，仅 4 条整句级硬特征（UPDATE..SET / INSERT INTO / DELETE FROM /
 * (AND|OR|WHERE) 等式），避免把普通业务文本误当注入清除。
 * union select、注释截断等激进规则不进默认表，经 spkit.security.sql-inject.addition-tainted-patterns
 * opt-in 追加（取舍与示例见 {@code SqlInjectProperties#additionTaintedPatterns} 字段注释）。
 */
public class SqlTaintedList {
    public static final List<String> TAINTED_PATTERN_LIST = new ArrayList<>();

    static {
        TAINTED_PATTERN_LIST.add("((?i)UPDATE[ ]+[a-zA-Z_0-9]*[ ]+SET)");
        TAINTED_PATTERN_LIST.add("((?i)INSERT[ ]+INTO [a-zA-Z_0-9]*[ ]+)");
        TAINTED_PATTERN_LIST.add("((?i)DELETE[ ]+FROM [a-zA-Z_0-9]*[ ]+)");
        TAINTED_PATTERN_LIST.add("((?i)[ ]+(AND|OR|WHERE)[ ]+[a-zA-Z_0-9]*=[a-zA-Z_0-9]*)");
    }
}
