package io.github.jevonsnotes.jevokit.desensitization.consts;

import io.github.jevonsnotes.jevokit.core.util.CollectionUtil;

import java.util.List;

/**
 * 脱敏密文协议常量：定义 SM4/MIXED 算法产出的密文信封格式与脱敏忽略名单。
 * 密文形态：{@code @DST([LV级别]内容密文|基础掩码)}（MIXED）/ {@code @DST([LV级别]内容密文)}（SM4），
 * 级别前缀供解密时提取权限档，{@code @} 开头同时是"已脱敏"的幂等标识——再次进入脱敏入口即跳过。
 * <p>关键约束：DESENSITIZATION_IGNORE_CLASS 列出的类型在日志脱敏遍历时跳过
 * （如 xxl-job 反射代理类，脱敏会破坏其内部状态）；模板常量改动即协议破坏性变更，
 * 已落库密文将无法解密。
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
