package springbootkit.configuration.security.sql.inject;

import lombok.Data;

@Data
public class SqlInjectMeta {
    boolean enabled;
    SqlInjectProperties.Mode mode;
}