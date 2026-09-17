package io.github.jevonsnotes.jevokit.desensitization.desensitizer;

import io.github.jevonsnotes.jevokit.desensitization.consts.DstAlgorithm;
import io.github.jevonsnotes.jevokit.desensitization.dto.DstContext;
import io.github.jevonsnotes.jevokit.desensitization.util.DesUtil;

import java.lang.reflect.Field;

/**
 * 自定义脱敏器 SPI：@DstProperty(type = CUSTOM) 时接管该字段的脱敏逻辑，
 * 允许业务按字段语义定制输出形态（如保留生日段、按长度自适应等）。
 * 使用方式：实现本接口并指定为注解的 desensitizer 属性（须保留无参构造，反射实例化后以 Class 缓存复用）。
 * <p>关键约束：返回值即最终输出——应返回脱敏后的新值而非修改入参；需要复用通用掩码能力时委托
 * {@link DesUtil}（参考 {@link DefaultDesensitizer} 的默认实现）。
 */
public interface Desensitizer<T> {

    /**
     * 对当前字段值执行脱敏。
     *
     * @param object      字段所属对象（供读取关联字段做联合判定）
     * @param currentField 当前字段（反射元数据）
     * @param currentVal  当前字段值（字符串化后的原值）
     * @param context     脱敏上下文（类型/级别来自 @DstProperty）
     * @return 脱敏后的新值
     */
    String des(T object, Field currentField, String currentVal, DstContext context);

}
