package io.github.jevonsnotes.jevokit.sample.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import io.github.jevonsnotes.jevokit.desensitization.annotation.DstProperty;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstType;
import io.github.jevonsnotes.jevokit.sample.desensitizer.CardDesensitizer;
import io.github.jevonsnotes.jevokit.workflow.vo.TaskVo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.Map;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "body")
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
public class TestVo{
   @XmlElement(name = "content")
   private String content;
//   @DstProperty(type = DstType.NAME)
   @NotNull(message = "名称不能为空")
   @XmlElement(name = "name")
   private String name;

   @DstProperty(type = DstType.CUSTOM,desensitizer = CardDesensitizer.class)
   @XmlElement(name = "idCard")
   private String idCard;

   @DstProperty(type = DstType.MOBILE)
   @XmlElement(name = "mobile")
   private String mobile;

   @DstProperty(type = DstType.EMAIL)
   @XmlElement(name = "email")
   private String email;

   @DstProperty(type = DstType.BANK_CARD)
   @XmlElement(name = "bankCard")
   private String bankCard;

   @DstProperty(type = DstType.ADDRESS)
   @XmlElement(name = "address")
   private String address;

   @DstProperty(type = DstType.PHONE)
   @XmlElement(name = "phone")
   private String phone;

   @DstProperty(type = DstType.BANK_LINE_NUM)
   @XmlElement(name = "comBankCard")
   private String comBankCard;
   @DstProperty(type = DstType.LICENSE_PLATE)
   @XmlElement(name = "licensePlate")
   private String licensePlate;

   @DstProperty(type = DstType.MOBILE)
   @XmlElement(name = "tags")
   private String[] tags;

   @DstProperty(type = DstType.MOBILE)
   @XmlElement(name = "tagMap")
   private Map<String,String> tagMap;

   private String idType;

   @Valid
   private ExcelVo excelVo;

   private TaskVo[] taskVos;


}
