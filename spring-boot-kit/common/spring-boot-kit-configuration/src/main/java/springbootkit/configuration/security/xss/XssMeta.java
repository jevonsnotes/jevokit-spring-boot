package springbootkit.configuration.security.xss;

import lombok.Data;

@Data
public class XssMeta {
    boolean enabled;
    XssProperties.Mode mode;
    boolean ignoreResponse = true;
}