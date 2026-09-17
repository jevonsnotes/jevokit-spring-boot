package io.github.jevonsnotes.jevokit.desensitization.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstAlgorithm;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstType;

/**
 * 脱敏执行上下文：一次脱敏动作的输入参数载体——脱什么类型（type）+ 按哪个权限档（level）。
 * 使用方式：注解链路由切面从 {@code @DstProperty} 读取填充；属性名规则链路由
 * {@code DesUtil.propPatternMatch} 匹配规则后构建；也可业务手工 builder 构造后调 {@code DesUtil.des}。
 * <p>关键约束：type 为空时 {@code DesUtil.des} 不执行脱敏（原样返回）；level 为空时 SM4/MIXED
 * 密文按 LV9（最低敏感档）内嵌，解密还原时依档位校验权限。
 */
@Data
@SuperBuilder
@Accessors(chain = true)
public class DstContext {
    private DstType type;
    private DstLevel level;
//    private DstAlgorithm algorithm;
}
