package io.github.jevonsnotes.jevokit.api.config;

import lombok.Data;
import io.github.jevonsnotes.jevokit.api.consts.ApiConst;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description api访问token配置
 * @Date 2023/3/10 14:16
 * @Version 0.0.1
 */
@Data
public class JevokitApiAccessProperties {
    private String header = ApiConst.API_HEADER_ACCESS_TOKEN;
    private List<JevokitApiAccessMapperProperties> tokens = new ArrayList<>();

}
