package springbootkit.core.util;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.net.url.UrlQuery;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description 请求工具类
 * @Date 2025/2/24
 * @Version 0.0.1
 */
public class RequestUtil {
    public static Map<String, List<String>> decodeParams(String paramsStr, String charset) {
        return decodeParams(paramsStr, CharsetUtil.charset(charset));
    }
    public static Map<String, List<String>> decodeParams(String paramsStr, Charset charset) {
        final Map<CharSequence, CharSequence> queryMap = UrlQuery.of(paramsStr, charset,false).getQueryMap();
        if (MapUtil.isEmpty(queryMap)) {
            return MapUtil.empty();
        }

        final Map<String, List<String>> params = new LinkedHashMap<>();
        queryMap.forEach((key, value) -> {
            final List<String> values = params.computeIfAbsent(StrUtil.str(key), k -> new ArrayList<>(1));
            // 一般是一个参数
            values.add(StrUtil.str(value));
        });
        return params;
    }
}
