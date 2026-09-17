package io.github.jevonsnotes.jevokit.nogen.vo;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 *
 * BizNoVo对象.
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BizNoVo implements Serializable {
	private static final long serialVersionUID = 1L;

	/** 对应字段：biz_tag */
	private String bizTag;
	/** 对应字段：max_id */
	private Long maxId;
	/** 对应字段：step */
	private Integer step;
	private Integer mode;
	/** 对应字段：threhold */
	private BigDecimal threhold;
	/** 对应字段：description */
	private String description;
	/** 对应字段：create_time */
	@JSONField(format = "yyyy-MM-dd HH:mm:ss")
	private Date createTime;
	/** 对应字段：update_time */
	@JSONField(format = "yyyy-MM-dd HH:mm:ss")
	private Date updateTime;
}
