package io.github.jevonsnotes.jevokit.okhttp.lang;

import java.util.HashMap;
import java.util.Map;

/**
 * 请求头链式构造器：add 逐条累积、build 产出一次性的 Map。
 * 与 OkHttpUtil 各 *WithHeaders 方法配套使用，仅为免手写 Map 的语法糖。
 */
public class HeaderBuilder {
    private final Map<String, String> headers = new HashMap<>();

    public HeaderBuilder add(String key, String value) {
        this.headers.put(key, value);
        return this;
    }

    public Map<String, String> build() {
        return headers;
    }
}
