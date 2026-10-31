package io.github.jevonsnotes.jevokit.sample.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

/**
 * 上传演示业务数据（JSecureUploadDto 泛型 B 的具体类型）：订单报销单。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
public class UploadOrderVo {

    /** 报销单号 */
    private String orderNo;

    /** 金额 */
    private Double amount;

    /** 提交人工号 */
    private String submitter;
}
