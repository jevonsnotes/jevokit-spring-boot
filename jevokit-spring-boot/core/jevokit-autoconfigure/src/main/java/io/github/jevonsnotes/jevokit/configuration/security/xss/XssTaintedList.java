package io.github.jevonsnotes.jevokit.configuration.security.xss;

import java.util.ArrayList;
import java.util.List;

/**
 * XSS 默认污染特征正则表：<b>默认为空</b>——不对任何特征做默认过滤，规则完全由用户经
 * jevokit.security.xss.addition-tainted-patterns 配置（刻意取舍：HTML 语境多样，默认清除极易误伤富文本；
 * 注释中保留 javascript: 作为示例参照）。运行期叠加逻辑见 {@code XssProperties#getAllTaintedPatterns}。
 */
public class XssTaintedList {
    public static final List<String> TAINTED_PATTERN_LIST = new ArrayList<>();

    static {
//        不做默认过滤，由用户控制
//        TAINTED_PATTERN_LIST.add("((?i)javascript:)");
    }
}
