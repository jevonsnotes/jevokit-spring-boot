package io.github.jevonsnotes.jevokit.api.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.github.jevonsnotes.jevokit.api.consts.ApiError;

/**
 * JSecure 协议响应报文：安全接口的出参信封，与 {@code JSecureRequest} 对称，承载状态码与数据密文。
 * 使用方式：Controller 返回 {@code JSecureResponse<T>}，用 {@link #ok(Object)}/{@link #fail(String)} 工厂构造；
 * target 业务实体序列化为 data 密文（由加密适配器完成），前端只收 data。
 * <p>关键约束：view（明文）与 target（实体）均标注不序列化，绝不出网；code 取 {@link ApiError} 协议码；
 * {@link #toCiphertextDto()}/{@link #toPlaintextDto()} 用于输出时裁剪对侧字段，防止明密文互泄。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JSecureResponse<T> {
    /**
     * 交易号（唯一）
     */
    private String transNo;
    /**
     * 交易类型
     */
    private String transType;
    /**
     * 状态码
     */
    private String code;
    /**
     * 消息提示
     */
    private String message;
    /**
     * 签名
     */
    private String signature;
    /**
     * 数据串（密文）
     */
    private String data;
    /**
     * 数据串（明文）
     */
    @JSONField(serialize = false)
    @JsonIgnore
    private String view;
    /**
     * 数据载体，不返回前端
     */
    @JsonIgnore
    @JSONField(serialize = false)
    private T target;


    /**
     * 成功响应（携带数据实体）。
     *
     * @param data 业务数据（经加密适配器转密文 data 后出网）
     * @return SUCCESS 协议响应
     */
    public static <T>JSecureResponse<T> ok(T data){
       return new JSecureResponse<>(null,null,ApiError.SUCCESS.code(), ApiError.SUCCESS.msg(), null,null,null,data);
    }
    /** 成功响应（无数据体）。 */
    public static <T>JSecureResponse<T> ok(){
       return new JSecureResponse<>(null,null,ApiError.SUCCESS.code(), ApiError.SUCCESS.msg(), null,null,null,null);
    }
    /**
     * 失败响应（取异常消息）。
     *
     * @param e 异常（message 作为提示，null 安全）
     * @return FAIL 协议响应
     */
    public static <T>JSecureResponse<T> fail(Throwable e){
       return new JSecureResponse<>(null,null,ApiError.FAIL.code(),e.getMessage(),null,null,null,null);
    }
    /** 失败响应（默认失败消息）。 */
    public static <T>JSecureResponse<T> fail(){
       return new JSecureResponse<>(null,null,ApiError.FAIL.code(),ApiError.FAIL.msg(),null,null,null,null);
    }
    /**
     * 失败响应（自定义消息）。
     *
     * @param message 提示消息
     * @return FAIL 协议响应
     */
    public static <T>JSecureResponse<T> fail(String message){
       return new JSecureResponse<>(null,null,ApiError.FAIL.code(),message,null,null,null,null);
    }
    /**
     * 失败响应（自定义消息；code 参数当前未生效，协议码恒为 FAIL，见类注释约束）。
     *
     * @param code    自定义码（保留参数，暂不写入 code 字段）
     * @param message 提示消息
     * @return FAIL 协议响应
     */
    public static <T>JSecureResponse<T> fail(String code,String message){
       return new JSecureResponse<>(null,null,ApiError.FAIL.code(),message,null,null,null,null);
    }

    /**
     * 裁剪为仅含密文的报文形态（view/target 置空）——出网前防明文外泄。
     *
     * @return 仅含 data 密文的新对象（原对象不变）
     */
    public JSecureResponse<T> toCiphertextDto(){
        return new JSecureResponse<>(this.transNo,this.transType,this.code,this.message,this.signature,this.data,null,null);
    }
    /**
     * 裁剪为仅含明文的报文形态（data/target 置空）——日志打印明文场景用。
     *
     * @return 仅含 view 明文的新对象（原对象不变）
     */
    public JSecureResponse<T> toPlaintextDto(){
        return new JSecureResponse<>(this.transNo,this.transType,this.code,this.message,this.signature,this.view,null,null);
    }
}
