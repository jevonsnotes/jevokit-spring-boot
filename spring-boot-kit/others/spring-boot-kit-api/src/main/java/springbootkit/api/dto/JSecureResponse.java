package springbootkit.api.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import springbootkit.api.consts.ApiError;

/**
 * @Description TODO
 * @Date 2025/4/10
 * @Version 0.0.1
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


    public static <T>JSecureResponse<T> ok(T data){
       return new JSecureResponse<>(null,null,ApiError.SUCCESS.code(), ApiError.SUCCESS.msg(), null,null,null,data);
    }
    public static <T>JSecureResponse<T> ok(){
       return new JSecureResponse<>(null,null,ApiError.SUCCESS.code(), ApiError.SUCCESS.msg(), null,null,null,null);
    }
    public static <T>JSecureResponse<T> fail(Throwable e){
       return new JSecureResponse<>(null,null,ApiError.FAIL.code(),e.getMessage(),null,null,null,null);
    }
    public static <T>JSecureResponse<T> fail(){
       return new JSecureResponse<>(null,null,ApiError.FAIL.code(),ApiError.FAIL.msg(),null,null,null,null);
    }
    public static <T>JSecureResponse<T> fail(String message){
       return new JSecureResponse<>(null,null,ApiError.FAIL.code(),message,null,null,null,null);
    }
    public static <T>JSecureResponse<T> fail(String code,String message){
       return new JSecureResponse<>(null,null,ApiError.FAIL.code(),message,null,null,null,null);
    }

    public JSecureResponse<T> toCiphertextDto(){
        return new JSecureResponse<>(this.transNo,this.transType,this.code,this.message,this.signature,this.data,null,null);
    }
    public JSecureResponse<T> toPlaintextDto(){
        return new JSecureResponse<>(this.transNo,this.transType,this.code,this.message,this.signature,this.view,null,null);
    }
}
