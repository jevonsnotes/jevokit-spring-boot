package io.github.jevonsnotes.jevokit.desensitization.consts;

import cn.hutool.core.util.StrUtil;

/**
 * 权限检查时机枚举：决定"有权限看明文的用户"在哪个环节拿到明文（spkit.desensitization.power-check-period）。
 * 使用方式：ENCRYPT——脱敏（加密）出口即检查权限，有权限直接返回原文（入库/出参均为明文）；
 * DECRYPT（默认）——加密时不检查统一产出密文，在解密入口（DesUtil.decrypt）检查权限。
 * <p>关键约束：默认 DECRYPT 保证落库内容始终脱敏（更安全），ENCRYPT 则把明文放到了出参环节，
 * 仅适用于内部高可信链路。
 */

public enum DstPowerCheckPeriod {

    DECRYPT,ENCRYPT;

}
