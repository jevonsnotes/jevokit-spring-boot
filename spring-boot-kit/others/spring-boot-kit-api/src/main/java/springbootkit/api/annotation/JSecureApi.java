package springbootkit.api.annotation;

import cn.hutool.core.util.StrUtil;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * @Description TODO
 * @Date 2025/4/10
 * @Version 0.0.1
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface JSecureApi {
    @AliasFor("transType")
    String[] value() default {};

    @AliasFor("value")
    String[] transType() default {};
//    /**
//     * 是否启用请求解密
//     */
//    boolean decrypt() default true;
//    /**
//     * 是否启用返回加密
//     */
//    boolean encrypt() default true;
//    /**
//     * 是否启用验签
//     */
//    boolean verify() default true;
//
//    /**
//     * 签名算法
//     */
//    JSecureSignAlg signAlg() default @JSecureSignAlg;
}
