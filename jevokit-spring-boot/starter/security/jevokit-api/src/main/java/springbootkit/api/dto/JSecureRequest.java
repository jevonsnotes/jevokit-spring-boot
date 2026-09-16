package springbootkit.api.dto;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * @Description TODO
 * @Date 2025/4/10
 * @Version 0.0.1
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JSecureRequest<T> {
    /**
     * 交易号（唯一）
     */
    @NotBlank(message = "transNo不能为空")
    private String transNo;
    /**
     * 交易类型
     */
    private String transType;
    /**
     * 签名
     */
    private String signature;
    /**
     * 数据串原文（密文）
     */
    private String data;
    /**
     * 数据串明文
     */
    private String view;
    /**
     * 数据串转实体对象
     */
    @NotNull(message = "data无法解析或者为空")
    @Valid
    private T target;

    public JSecureRequest<T> toCiphertextDto(){
        return new JSecureRequest<>(this.transNo, this.transType, this.signature, this.data,null, null);
    }
    public JSecureRequest<T> toPlaintextDto(){
        return new JSecureRequest<>(this.transNo, this.transType, this.signature, this.view, null, null);
    }
}
