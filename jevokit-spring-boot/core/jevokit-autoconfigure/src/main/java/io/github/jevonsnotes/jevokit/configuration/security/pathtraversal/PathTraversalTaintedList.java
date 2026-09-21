package io.github.jevonsnotes.jevokit.configuration.security.pathtraversal;

import java.util.ArrayList;
import java.util.List;

/**
 * 路径穿越默认污染特征正则表：刻意保守，仅收录 ../ 目录回溯与 /etc/ 敏感路径两类硬特征。
 * 更多特征（如编码变体 %2e%2e%2f）请经 jevokit.security.path-traversal.addition-tainted-patterns 追加，
 * 见 {@code PathTraversalProperties#additionTaintedPatterns}。
 */
public class PathTraversalTaintedList {
    public static final List<String> TAINTED_PATTERN_LIST = new ArrayList<>();

    static {
        TAINTED_PATTERN_LIST.add("\\.\\./");
        TAINTED_PATTERN_LIST.add("/etc/.*");
    }
}
