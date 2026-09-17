package io.github.jevonsnotes.jevokit.security.pt.util;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import io.github.jevonsnotes.jevokit.configuration.security.pathtraversal.PathTraversalProperties;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;
import io.github.jevonsnotes.jevokit.security.pt.exception.PathTraversalException;

import java.util.List;

/**
 * @Description TODO
 * @Date 2022/5/24 18:14
 * @Version 0.0.1
 */
public class PathTraversalUtil {

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
