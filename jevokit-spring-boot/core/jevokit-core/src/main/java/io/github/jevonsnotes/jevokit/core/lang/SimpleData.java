package io.github.jevonsnotes.jevokit.core.lang;

import lombok.Data;

/**
 * 单值数据信封：只含一个 data 字段的最小包装，用于 REST 接口收发单个裸值（如仅提交/返回一个字符串密文），
 * 使报文成为合法 JSON 对象而非裸标量。
 * <p>泛型参数 T 为被包裹的负载数据类型。
 */
@Data
public class SimpleData<T> {
    private T data;
}
