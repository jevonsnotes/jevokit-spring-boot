package springbootkit.sample.vo;

import lombok.Data;
import springbootkit.core.annotation.BeanField;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;

/**
 * @Description TODO
 * @Date 2022/7/6 10:51
 * @Version 0.0.1
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
