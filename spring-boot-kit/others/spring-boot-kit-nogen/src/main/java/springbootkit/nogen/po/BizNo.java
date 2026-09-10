package springbootkit.nogen.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Table(name = "biz_no")
public class BizNo implements Serializable {
	private static final long serialVersionUID = 1L;
	@Column(name = "biz_tag")
	/** 对应字段：biz_tag */
	private String bizTag;
	/** 对应字段：max_id */
	@Column(name = "max_id")
	private Long maxId;
	/** 对应字段：step */
	@Column(name = "step")
	private Integer step;
	@Column(name = "mode")
	private Integer mode;
	/** 对应字段：threhold */
	@Column(name = "threhold")
	private BigDecimal threhold;
	/** 对应字段：description */
	@Column(name = "description")
	private String description;
	/** 对应字段：create_time */
	@Column(name = "create_time")
	private Date createTime;
	/** 对应字段：update_time */
	@Column(name = "update_time")
	private Date updateTime;
}
