package io.github.jevonsnotes.jevokit.api.annotation;

import cn.hutool.core.util.StrUtil;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * 安全接口协议注解：标注在 Controller 方法上，声明该接口走 JSecure 加解密报文协议
 * （请求体 {@code JSecureRequest<T>} 解密验签、响应体 {@code JSecureResponse<T>} 加密签名）。
 * 使用方式：方法参数声明为 {@code JSecureRequest<业务DTO>} 并加本注解（可选指定 transType 限定交易类型），
 * {@code DecryptRequestAdviceAdapter}/{@code EncryptResponseAdviceAdapter} 依此注解识别并处理。
 * <p>关键约束：transType 未指定时不做交易类型校验；value 与 transType 互为别名（@AliasFor），二选一即可。
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
