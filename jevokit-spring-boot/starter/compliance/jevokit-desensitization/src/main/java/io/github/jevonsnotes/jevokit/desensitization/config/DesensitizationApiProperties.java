package io.github.jevonsnotes.jevokit.desensitization.config;

import lombok.Data;

/**
 * @Description TODO
 * @Date 2024/11/22
 * @Version 0.0.1
 */
@Data
public class DesensitizationApiProperties {
    private boolean enabled = false;
    private String accessToken;
}
