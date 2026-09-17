package io.github.jevonsnotes.jevokit.security.xss.cleaner;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.springframework.web.util.HtmlUtils;
import io.github.jevonsnotes.jevokit.configuration.security.xss.XssProperties;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;
import io.github.jevonsnotes.jevokit.security.xss.bean.HtmlSafeList;
import io.github.jevonsnotes.jevokit.security.xss.exception.XssException;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RequiredArgsConstructor
public class DefaultXssCleaner implements XssCleaner{

    private final XssProperties properties;

    /**
     * 按当前请求模式清洗：escape 实体化转义；reject 命中即抛 {@link XssException}；
     * clear 走 jsoup 白名单净化 + 附加污染表剥离（三模式输出形态与幂等语义见类注释与
     * {@link XssCleaner#clean(String)}）。
     */
    @Override
    public String clean(String content) {
        if(StrUtil.isNotBlank(content)){
            //转义模式
            if(XssProperties.Mode.escape == ConfigHolder.getXssMeta().getMode()){
                return additionClean(HtmlUtils.htmlEscape(content, StandardCharsets.UTF_8.name()));
            } else if(XssProperties.Mode.reject == ConfigHolder.getXssMeta().getMode()){
                //校验不通过，拒绝交互
                if(!isValid(content)){
                    throw new XssException("Process denied. The exchanged content is suspected to be an XSS attack!");
                }
            }
            //清理模式
            else {
                // 清洗输出为 HTML 片段语义：正文 & 等按规范实体化（幂等，& 与 &amp; 输出均收敛为 &amp;）；
                // 非 HTML 消费方自行 htmlUnescape；需原文直通场景用 reject 模式白名单放行（BUG-11 wontfix 裁定）
                Document.OutputSettings outputSettings = new Document
                        .OutputSettings()
                        .escapeMode(Entities.EscapeMode.xhtml)
                        .prettyPrint(false);

                return  additionClean(Jsoup.clean(content,"", HtmlSafeList.INSTANCE,outputSettings));
            }

        }
        return content;
    }

    /**
     * reject 模式校验：jsoup 白名单校验 + 附加污染表逐条包含匹配，全部通过才算合法。
     *
     * @param content 待校验内容
     * @return true 合法（白名单内且无附加污染命中）
     */
    private boolean isValid(String content){
        if(!Jsoup.isValid(content,HtmlSafeList.INSTANCE)){
            return false;
        }
        List<String> taintedPatterns = properties.getAllTaintedPatterns();
        for(String pattern: taintedPatterns){
            if(content.matches("[\\s\\S]*"+pattern+"[\\s\\S]*")){
                return false;
            }
        }
        return true;
    }

    /**
     * 附加污染表后处理：clear 模式逐条 replaceAll 剥离；reject 模式命中即抛异常
     * （对 escape/clean 主流程产出的串做二次加固，规则来自 addition-tainted-patterns）。
     */
    private String additionClean(String content){
        List<String> taintedPatterns = properties.getAllTaintedPatterns();
        if(StrUtil.isNotBlank(content) && CollectionUtil.isNotEmpty(taintedPatterns)){
            if(XssProperties.Mode.clear == ConfigHolder.getXssMeta().getMode()){
                for(String pattern: taintedPatterns){
                    content = content.replaceAll(pattern,"");
                }
            } else if (XssProperties.Mode.reject ==ConfigHolder.getXssMeta().getMode()){
                for(String pattern: taintedPatterns){
                    if(content.matches("[\\s\\S]*"+pattern+"[\\s\\S]*")){
                        throw new XssException("Process denied. The exchanged content is suspected to be an xss attack!");
                    }
                }
            }
        }
        return content;
    }
}
