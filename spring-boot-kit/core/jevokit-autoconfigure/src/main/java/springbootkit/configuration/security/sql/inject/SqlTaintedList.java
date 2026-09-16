package springbootkit.configuration.security.sql.inject;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description TODO
 * @Date 2022/5/25 8:56
 * @Version 0.0.1
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
