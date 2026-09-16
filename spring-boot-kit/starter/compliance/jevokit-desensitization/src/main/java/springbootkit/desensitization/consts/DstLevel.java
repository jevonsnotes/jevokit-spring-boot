package springbootkit.desensitization.consts;

import cn.hutool.core.util.StrUtil;

/**
 * @Description TODO
 * @Date 2022/7/11 16:37
 * @Version 0.0.1
 */
public enum DstLevel {

    LV0,LV1,LV2,LV3,LV4,LV5,LV6,LV7,LV8,LV9;

    public static DstLevel of(String level) {
        if(StrUtil.isNotBlank(level)) {
            try {
                return DstLevel.valueOf(level);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }
}
