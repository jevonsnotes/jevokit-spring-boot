package io.github.jevonsnotes.jevokit.core.util;

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
 * 请求工具类
 */
public class RequestUtil {
    /**
     * 解析 URL 查询串为参数 Map
     *
     * @param paramsStr 查询串（不含 '?'，如 a=1&amp;b=2）
     * @param charset   字符集名，空白时 hutool 默认 UTF-8
     * @return 参数名到值列表的有序 Map（同名参数聚合为多值列表）；入参为空时返回空 Map
     */
    public static Map<String, List<String>> decodeParams(String paramsStr, String charset) {
        return decodeParams(paramsStr, CharsetUtil.charset(charset));
    }
    /**
     * 解析 URL 查询串为参数 Map
     *
     * @param paramsStr 查询串（不含 '?'）
     * @param charset   字符集
     * @return 参数名到值列表的有序 Map（同名参数聚合为多值列表）；入参为空时返回空 Map
     */
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
