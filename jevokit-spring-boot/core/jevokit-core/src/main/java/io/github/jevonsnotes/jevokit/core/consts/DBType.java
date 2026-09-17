package io.github.jevonsnotes.jevokit.core.consts;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支持的数据库方言枚举：配合 {@link DBConst} 的异常特征正则，供自动建表等组件识别当前数据库类型。
 * jdbcName 为 JDBC URL 中的方言标识（如 jdbc:postgresql://... 的 "postgresql"）。
 */
@Getter
@AllArgsConstructor
public enum DBType {
    MYSQL("mysql"),
    POSTGRESQL("postgresql"),
    H2("h2"),
    ;
    private String jdbcName;
}
