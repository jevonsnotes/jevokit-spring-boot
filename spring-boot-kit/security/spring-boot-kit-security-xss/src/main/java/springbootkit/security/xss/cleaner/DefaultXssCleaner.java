package springbootkit.security.xss.cleaner;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.springframework.web.util.HtmlUtils;
import springbootkit.configuration.security.xss.XssProperties;
import springbootkit.configuration.holder.ConfigHolder;
import springbootkit.security.xss.bean.HtmlSafeList;
import springbootkit.security.xss.exception.XssException;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RequiredArgsConstructor
public class DefaultXssCleaner implements XssCleaner{

    private final XssProperties properties;

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
                Document.OutputSettings outputSettings = new Document
                        .OutputSettings()
                        .escapeMode(Entities.EscapeMode.xhtml)
                        .prettyPrint(false);

                return  additionClean(Jsoup.clean(content,"", HtmlSafeList.INSTANCE,outputSettings));
            }

        }
        return content;
    }

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
