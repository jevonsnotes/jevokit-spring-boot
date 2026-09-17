package io.github.jevonsnotes.jevokit.core.consts;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Description TODO
 * @Date 2023/3/10 16:31
 * @Version 0.0.1
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
