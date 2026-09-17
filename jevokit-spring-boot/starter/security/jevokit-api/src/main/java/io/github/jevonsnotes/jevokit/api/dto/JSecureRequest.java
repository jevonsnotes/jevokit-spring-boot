package io.github.jevonsnotes.jevokit.api.dto;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * JSecure 协议请求报文：安全接口的入参信封，承载交易标识、签名与数据密文。
 * 使用方式：Controller 方法声明 {@code JSecureRequest<业务DTO>} + {@code @JSecureApi}；线上传输时只带
 * data（密文），解密适配器完成验签解密后填充 view（明文）与 target（反序列化实体），业务直接读 target。
 * <p>关键约束：transNo 唯一、data 不可解析/为空时校验失败（@NotNull + @Valid 逐层级联校验）；
 * {@link #toCiphertextDto()}/{@link #toPlaintextDto()} 用于输出时裁剪对侧字段，防止明密文互泄。
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

    /**
     * 裁剪为仅含密文的报文形态（view/target 置空）——输出/转发密文场景用，防明文外泄。
     *
     * @return 仅含 data 密文的新对象（原对象不变）
     */
    public JSecureRequest<T> toCiphertextDto(){
        return new JSecureRequest<>(this.transNo, this.transType, this.signature, this.data,null, null);
    }
    /**
     * 裁剪为仅含明文的报文形态（data/target 置空）——日志打印明文场景用，防密文混淆。
     *
     * @return 仅含 view 明文的新对象（原对象不变）
     */
    public JSecureRequest<T> toPlaintextDto(){
        return new JSecureRequest<>(this.transNo, this.transType, this.signature, this.view, null, null);
    }
}
