package io.github.jevonsnotes.jevokit.security.pt.util;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import io.github.jevonsnotes.jevokit.configuration.security.pathtraversal.PathTraversalProperties;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;
import io.github.jevonsnotes.jevokit.security.pt.exception.PathTraversalException;

import java.util.List;

/**
 * 路径穿越（Path Traversal）检测/清洗工具：按当前请求线程的防护模式
 * （jevokit.security.path-traversal.mode）处理入参字符串。
 * 由 {@link io.github.jevonsnotes.jevokit.security.pt.chain.PtStringPropertiesEditChain}
 * 在表单/请求体字符串属性绑定时调用；也可在业务代码中显式调用本工具做同规则校验。
 * <p>命中语义：规则表 = 默认污染表 + addition-tainted-patterns 追加表（见
 * {@link io.github.jevonsnotes.jevokit.configuration.security.pathtraversal.PathTraversalProperties#getAllTaintedPatterns()}）。
 * 默认污染表仅 2 条：{@code ../}（相对路径回溯）与 {@code /etc/.*}（Unix 敏感目录，见
 * {@link io.github.jevonsnotes.jevokit.configuration.security.pathtraversal.PathTraversalTaintedList}）。
 * <p>清洗后形态与误伤边界：
 * <ul>
 *   <li>clear 模式：逐条 {@code replaceAll} 剥离命中片段（如 {@code "../etc/passwd"} 清洗为 {@code "etc/passwd"}，
 *       回溯段被移除）；空白串、规则表为空时原样返回；</li>
 *   <li>reject 模式：以 {@code [\s\S]*pattern[\s\S]*} 整串包含匹配，任一规则命中即抛
 *       {@link PathTraversalException} 拒绝整条报文。</li>
 * </ul>
 * 误伤提示：{@code /etc/.*} 为全量前缀匹配，业务若合法接收以 /etc/ 开头的路径（如配置管理端），
 * 应通过 addition 规则调整或改用 reject 模式白名单放行。
 * 约束：防护模式读取自 {@link io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder} 的 ThreadLocal，
 * 由 PathTraversalInterceptor 在 preHandle 写入、请求结束后清理；仅在请求线程内调用有效。
 */
public class PathTraversalUtil {

    /**
     * 按当前线程模式清洗/校验入参字符串（检测语义与误伤边界见类注释）。
     *
     * @param content         待处理字符串；为空白（null/空/全空白）时原样返回
     * @param taintedPatterns 污染正则表（通常来自 getAllTaintedPatterns() 的合并结果）；为空集合时原样返回
     * @return clear 模式下返回剥离命中片段后的字符串；reject 模式下未命中时返回原串
     * @throws PathTraversalException reject 模式下任一规则包含命中时抛出，表示拒绝本次交互
     */
    public static String clean(String content, List<String> taintedPatterns){

        if(StrUtil.isNotBlank(content) && CollectionUtil.isNotEmpty(taintedPatterns)){
            if(PathTraversalProperties.Mode.clear == ConfigHolder.getPtMeta().getMode()){
                for(String pattern: taintedPatterns){
                    content = content.replaceAll(pattern,"");
                }
            } else if (PathTraversalProperties.Mode.reject ==ConfigHolder.getPtMeta().getMode()){
                for(String pattern: taintedPatterns){
                    if(content.matches("[\\s\\S]*"+pattern+"[\\s\\S]*")){
                        throw new PathTraversalException("Process denied. The exchanged content is suspected to be a Path-Traversal attack!");
                    }
                }
            }
        }
        return content;
    }

}
