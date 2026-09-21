package io.github.jevonsnotes.jevokit.security.code.inject.util;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import io.github.jevonsnotes.jevokit.configuration.security.code.inject.CodeInjectProperties;

import java.util.List;
import java.util.Optional;

/**
 * 动态代码注入校验工具：按命名空间正则规则判定字符串是否"安全形态"，供业务在拼装/执行
 * 动态代码（Groovy/SpEL/JS 脚本等）前做白/黑名单校验。
 * 使用方式：由 {@link io.github.jevonsnotes.jevokit.security.code.inject.config.CodeInjectConfiguration}
 * 注册为 bean 并注入 {@link io.github.jevonsnotes.jevokit.configuration.security.code.inject.CodeInjectProperties}
 * （规则表 jevokit.security.code-inject.namespaces），业务侧静态调用 {@link #isSafe(String)} 判定或
 * {@link #errorIfUnsafe(String)} 校验即返回/抛错。
 * <p>判定语义（关键约束）：
 * <ul>
 *   <li>规则按 namespace 分组（默认命名空间 {@code default}），对 val 逐条全量匹配（{@code matches}，整串匹配非包含）；</li>
 *   <li>{@code negative=false}（默认，白名单）：val 必须匹配该命名空间下<b>全部</b>规则才算安全，任一条
 *       不匹配即不安全；{@code negative=true}（黑名单）：val 匹配<b>任一</b>规则即不安全；</li>
 *   <li>命名空间无规则、namespaces 未配置或 val 为空串时视为安全（防御性默认放行——无规则即无校验依据，
 *       不阻断业务）。</li>
 * </ul>
 * 注意：静态方法依赖 Spring 容器完成属性注入后可用；bean 未装配（如 jevokit.security.code-inject.enabled=false）
 * 时调用会因配置对象为 null 而 NPE，请保持默认开启或确保容器初始化完成后再使用。
 */
public class CodeInjectUtil {

    private static CodeInjectProperties properties;

    /**
     * 判断字符串是否为安全形态（判定语义与白/黑名单规则见类注释）。
     *
     * @param namespace 规则命名空间（jevokit.security.code-inject.namespaces 下的键）
     * @param val       待判定字符串；空白（null/空串）视为安全（无内容可注入）
     * @return true 安全（含命名空间无规则/未配置 namespaces 的默认放行）；false 不安全
     */
    public static boolean isSafe(String namespace,String val){
        if(properties.getNamespaces() != null && StrUtil.isNotEmpty(val)){

            List<String> patterns = CollectionUtil.isNotEmpty(properties.getNamespaces()) ? properties.getNamespaces().get(namespace) : null;
            if(CollectionUtil.isNotEmpty(patterns)){
                for(String pattern : patterns){
                    if(!properties.isNegative() && !val.matches(pattern)
                            || properties.isNegative() && val.matches(pattern)){
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * 校验不安全即抛错的快捷入口（默认命名空间 {@code default}）。
     *
     * @param val 待校验字符串
     * @return 校验通过时原样返回 val（便于链式使用）
     * @throws RuntimeException 判定不安全时抛出，消息含命名空间与原值（供日志定位）
     */
    public static String errorIfUnsafe(String val){
        return errorIfUnsafe(CodeInjectProperties.DEFAULT_PATTERNS_NAMESPACE,val);
    }

    /**
     * 校验不安全即抛错的快捷入口（判定语义见 {@link #isSafe(String, String)}）。
     *
     * @param namespace 规则命名空间
     * @param val       待校验字符串
     * @return 校验通过时原样返回 val（便于链式使用）
     * @throws RuntimeException 判定不安全时抛出，消息含命名空间与原值（供日志定位）
     */
    public static String errorIfUnsafe(String namespace,String val){
        boolean safe = isSafe(namespace, val);
        if(safe){
            return val;
        } else {
            throw new RuntimeException("unsafe code inject error, namespace ["+namespace+"] , val ["+val+"]" );
        }
    }
    /**
     * 判断是否安全（使用默认命名空间 default，判定语义同 {@link #isSafe(String, String)}）。
     *
     * @param val 待判定字符串；空白视为安全
     * @return true 安全；false 不安全
     */
    public static boolean isSafe(String val){
        return isSafe(CodeInjectProperties.DEFAULT_PATTERNS_NAMESPACE,val);
    }

    /**
     * 向静态工具注入配置的 Spring 回调：容器启动时经
     * {@link io.github.jevonsnotes.jevokit.security.code.inject.config.CodeInjectConfiguration} 装配触发，
     * 静态方法依赖此注入完成（见类注释的初始化约束）。
     */
    @Autowired
    public void initProperties(CodeInjectProperties properties){
        this.properties = properties;
    }
}
