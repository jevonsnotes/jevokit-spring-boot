package io.github.jevonsnotes.jevokit.api.config;

import lombok.Data;
import io.github.jevonsnotes.jevokit.api.consts.ApiConst;

import java.util.ArrayList;
import java.util.List;

/**
 * api访问token配置
 */
@Data
public class JevokitApiAccessProperties {
    private String header = ApiConst.API_HEADER_ACCESS_TOKEN;
    private List<JevokitApiAccessMapperProperties> tokens = new ArrayList<>();

}
