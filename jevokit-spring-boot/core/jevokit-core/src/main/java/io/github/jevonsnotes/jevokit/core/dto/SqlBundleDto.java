package io.github.jevonsnotes.jevokit.core.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 预处理 SQL 捆绑包：{@link #sql} 为含 ? 占位符的语句，{@link #args} 为与占位符顺序一致的参数数组。
 * 由 {@code SqlUtil.generateXxxPreSql} 系列产出，直接交给 JdbcTemplate 等 execute(sql, args) 消费。
 */
@Data
@Accessors(chain = true)
public class SqlBundleDto {
    private String sql;
    private Object[] args;
}
