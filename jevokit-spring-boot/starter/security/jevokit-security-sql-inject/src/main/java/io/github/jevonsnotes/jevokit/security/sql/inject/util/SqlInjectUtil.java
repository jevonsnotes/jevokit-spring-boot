package io.github.jevonsnotes.jevokit.security.sql.inject.util;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import io.github.jevonsnotes.jevokit.configuration.security.pathtraversal.PathTraversalProperties;
import io.github.jevonsnotes.jevokit.configuration.security.sql.inject.SqlInjectProperties;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;
import io.github.jevonsnotes.jevokit.security.sql.inject.exception.SqlInjectException;

import java.util.List;

/**
 * @Description TODO
 * @Date 2022/5/24 18:14
 * @Version 0.0.1
 */
public class SqlInjectUtil {

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
