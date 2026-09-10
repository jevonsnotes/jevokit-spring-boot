package springbootkit.api.util;

/**
 * @Description TODO
 * @Date 2025/4/11
 * @Version 0.0.1
 */
public class ApiUtil {
    public static String rebuildHeader(String key, String prefix) {
        return prefix.endsWith("-") ? prefix + key : prefix + "-" + key;
    }
}
