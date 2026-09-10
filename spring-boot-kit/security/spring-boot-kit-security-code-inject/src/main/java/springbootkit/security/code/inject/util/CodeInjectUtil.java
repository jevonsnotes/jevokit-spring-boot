package springbootkit.security.code.inject.util;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import springbootkit.configuration.security.code.inject.CodeInjectProperties;

import java.util.List;
import java.util.Optional;

/**
 * @Description TODO
 * @Date 2022/5/25 10:09
 * @Version 0.0.1
 */
public class CodeInjectUtil {

    private static CodeInjectProperties properties;

    /**
     * 判断是否安全
     * @param namespace 规则命名空间
     * @param val
     * @return 不存在规则时，默认返回true
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

    public static String errorIfUnsafe(String val){
        return errorIfUnsafe(CodeInjectProperties.DEFAULT_PATTERNS_NAMESPACE,val);
    }

    public static String errorIfUnsafe(String namespace,String val){
        boolean safe = isSafe(namespace, val);
        if(safe){
            return val;
        } else {
            throw new RuntimeException("unsafe code inject error, namespace ["+namespace+"] , val ["+val+"]" );
        }
    }
    /**
     * 判断是否安全（使用默认命名空间：default）
     * @param val
     * @return
     */
    public static boolean isSafe(String val){
        return isSafe(CodeInjectProperties.DEFAULT_PATTERNS_NAMESPACE,val);
    }

    @Autowired
    public void initProperties(CodeInjectProperties properties){
        this.properties = properties;
    }
}
