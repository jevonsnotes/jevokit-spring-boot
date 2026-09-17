package io.github.jevonsnotes.jevokit.okhttp.lang;

import java.util.HashMap;
import java.util.Map;

 /**
 * 请求头链式构造器：add 逐条累积、build 产出一次性的 Map。
 * 与 OkHttpUtil 各 *WithHeaders 方法配套使用，仅为免手写 Map 的语法糖。
 */
public class HeaderBuilder {
    private final Map<String, String> headers = new HashMap<>();

    /**
    * 追加一个请求头。
    * @param key 头字段名
    * @param value 头字段值
    * @return this（链式）
    */
    public HeaderBuilder add(String key, String value) {
        this.headers.put(key, value);
        return this;
    }

    /**
    * 产出累积的请求头 Map（内部 Map 直接返回，非拷贝——build 后勿再 add）。
    * @return 请求头集合
    */
    public Map<String, String> build() {
        return headers;
    }
}
