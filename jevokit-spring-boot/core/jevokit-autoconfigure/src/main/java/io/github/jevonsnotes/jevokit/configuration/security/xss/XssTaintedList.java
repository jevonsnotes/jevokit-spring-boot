package io.github.jevonsnotes.jevokit.configuration.security.xss;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description TODO
 * @Date 2022/5/25 11:32
 * @Version 0.0.1
 */
public class XssTaintedList {
    public static final List<String> TAINTED_PATTERN_LIST = new ArrayList<>();

    static {
//        不做默认过滤，由用户控制
//        TAINTED_PATTERN_LIST.add("((?i)javascript:)");
    }
}
