package springbootkit.okhttp.lang;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description TODO
 * @Date 2024/9/5
 * @Version 0.0.1
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
