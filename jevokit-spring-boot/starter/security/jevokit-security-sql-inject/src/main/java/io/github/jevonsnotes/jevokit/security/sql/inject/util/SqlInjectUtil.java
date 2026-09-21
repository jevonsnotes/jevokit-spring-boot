package io.github.jevonsnotes.jevokit.security.sql.inject.util;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import io.github.jevonsnotes.jevokit.configuration.security.pathtraversal.PathTraversalProperties;
import io.github.jevonsnotes.jevokit.configuration.security.sql.inject.SqlInjectProperties;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;
import io.github.jevonsnotes.jevokit.security.sql.inject.exception.SqlInjectException;

import java.util.List;

/**
 * SQL 注入检测/清洗工具：按当前请求线程的防护模式（jevokit.security.sql-inject.mode）处理入参字符串。
 * 由 {@link io.github.jevonsnotes.jevokit.security.sql.inject.chain.SqlInjectStringPropertiesEditChain}
 * 在表单/请求体字符串属性绑定时调用；也可在业务代码中显式调用本工具对任意字符串做同规则校验。
 * <p>命中语义：规则表 = 默认污染表 + addition-tainted-patterns 追加表（合并去重，见
 * {@link io.github.jevonsnotes.jevokit.configuration.security.sql.inject.SqlInjectProperties#getAllTaintedPatterns()}），
 * 逐条正则独立判定。默认污染表保持保守（仅 UPDATE..SET / INSERT INTO / DELETE FROM / (AND|OR|WHERE) 等式 4 条，
 * 见 {@link io.github.jevonsnotes.jevokit.configuration.security.sql.inject.SqlTaintedList}）。
 * <p>清洗后形态与误伤边界：
 * <ul>
 *   <li>clear 模式：逐条 {@code replaceAll} 剥离命中片段后原样放行（如 {@code "1 OR a=b"} 清洗为 {@code "1 "}），
 *       空白串、规则表为空时原样返回，不改变业务入参；</li>
 *   <li>reject 模式：以 {@code [\s\S]*pattern[\s\S]*} 整串包含匹配，任一规则命中即抛
 *       {@link SqlInjectException} 拒绝整条报文（无清洗形态）。</li>
 * </ul>
 * 约束：防护模式读取自 {@link io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder} 的 ThreadLocal，
 * 由 SqlInjectInterceptor 在 preHandle 写入、请求结束后清理；仅在请求线程内调用有效。
 */
public class SqlInjectUtil {

    /**
     * 按当前线程模式清洗/校验入参字符串（检测语义与误伤边界见类注释）。
     *
     * @param content         待处理字符串；为空白（null/空/全空白）时原样返回
     * @param taintedPatterns 污染正则表（通常来自 getAllTaintedPatterns() 的合并结果）；为空集合时原样返回
     * @return clear 模式下返回剥离命中片段后的字符串；reject 模式下未命中时返回原串
     * @throws SqlInjectException reject 模式下任一规则包含命中时抛出，表示拒绝本次交互
     */
    public static String clean(String content, List<String> taintedPatterns){

        if(StrUtil.isNotBlank(content) && CollectionUtil.isNotEmpty(taintedPatterns)){
            if(SqlInjectProperties.Mode.clear == ConfigHolder.getSIMeta().getMode()){
                for(String pattern: taintedPatterns){
                    content = content.replaceAll(pattern,"");
                }
            } else if (SqlInjectProperties.Mode.reject ==ConfigHolder.getSIMeta().getMode()){
                for(String pattern: taintedPatterns){
                    if(content.matches("[\\s\\S]*"+pattern+"[\\s\\S]*")){
                        throw new SqlInjectException("Process denied. The exchanged content is suspected to be a sql-Inject attack!");
                    }
                }
            }
        }
        return content;
    }



}
