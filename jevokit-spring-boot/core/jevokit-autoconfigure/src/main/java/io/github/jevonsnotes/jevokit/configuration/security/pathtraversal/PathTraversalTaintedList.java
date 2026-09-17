package io.github.jevonsnotes.jevokit.configuration.security.pathtraversal;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description TODO
 * @Date 2022/5/25 8:56
 * @Version 0.0.1
 */
public class PathTraversalTaintedList {
    public static final List<String> TAINTED_PATTERN_LIST = new ArrayList<>();

    static {
        TAINTED_PATTERN_LIST.add("\\.\\./");
        TAINTED_PATTERN_LIST.add("/etc/.*");
    }
}
