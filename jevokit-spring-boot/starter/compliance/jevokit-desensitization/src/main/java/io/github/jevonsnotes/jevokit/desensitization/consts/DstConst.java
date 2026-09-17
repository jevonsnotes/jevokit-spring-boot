package io.github.jevonsnotes.jevokit.desensitization.consts;

import io.github.jevonsnotes.jevokit.core.util.CollectionUtil;

import java.util.List;

/**
 * @Description TODO
 * @Date 2024/11/20
 * @Version 0.0.1
 */
public class DstConst {
    public static final String DESENSITIZED_ENC_KEY = "@DST";
    public static final String DESENSITIZED_ENC_LEVEL_PREFIX = "[";
    public static final String DESENSITIZED_ENC_LEVEL_SUFFIX = "]";
    public static final String DESENSITIZED_ENC_CONTENT_PREFIX = "(";
    public static final String DESENSITIZED_ENC_CONTENT_SUFFIX = ")";
    public static final String DESENSITIZED_ENC_SPILT = "|";

    public static final String DESENSITIZED_ENC_MIXED_TEMPLATE =
            DESENSITIZED_ENC_KEY+ DESENSITIZED_ENC_CONTENT_PREFIX+"%s" + DESENSITIZED_ENC_SPILT +"%s"+DESENSITIZED_ENC_CONTENT_SUFFIX;
    public static final String DESENSITIZED_ENC_ASE_TEMPLATE =
            DESENSITIZED_ENC_KEY + DESENSITIZED_ENC_CONTENT_PREFIX+"%s" +DESENSITIZED_ENC_CONTENT_SUFFIX;
    public static final String DESENSITIZED_ENC_ASE_SUB_TEMPLATE =
            DESENSITIZED_ENC_LEVEL_PREFIX+"%s"+DESENSITIZED_ENC_LEVEL_SUFFIX+"%s";

    public static final List<String> DESENSITIZATION_IGNORE_CLASS
            = CollectionUtil.toList(
                    "com.xxl.job.core.handler.impl.MethodJobHandler"
    );
}
