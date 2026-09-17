package io.github.jevonsnotes.jevokit.sample.vo;

import lombok.Data;
import io.github.jevonsnotes.jevokit.core.annotation.BeanField;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;

 /**
 * excel 导入示例行对象：@BeanField(order) 声明列序，@NotNull 演示导入校验，
 * 与 ExcelApi 的 @Excel 绑定配套。
 */
@Data
public class ExcelVo implements Serializable {
    @NotNull(message = "pkey不能为空")
    @BeanField(order = 1)
    private int pkey;
    @BeanField(order = 3)
    private String policyNo;
    @BeanField(order = 2)
    private String handlerCode;
    @BeanField(order = 4)
    @NotNull
    private Date date;

}
