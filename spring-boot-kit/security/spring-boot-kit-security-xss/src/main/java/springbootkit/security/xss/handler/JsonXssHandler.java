package springbootkit.security.xss.handler;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import springbootkit.configuration.holder.ConfigHolder;
import springbootkit.security.xss.cleaner.XssCleaner;
import springbootkit.configuration.security.xss.XssProperties;

import java.io.IOException;
/**
 * @Description json提交处理器
 * @Date 2022/5/23 15:33
 * @Version 0.0.1
 */
@Slf4j
@RequiredArgsConstructor
public class JsonXssHandler extends JsonDeserializer<String> {

    private final XssProperties properties;
    private final XssCleaner xssCleaner;

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        // XSS filter
        String text = p.getValueAsString();
        if (text == null) {
            return null;
        }
        if (ConfigHolder.getXssMeta().isEnabled()) {
            String value = xssCleaner.clean(properties.isTrimText()? StrUtil.trim(text) : text);
            log.debug("Json property value:{} cleaned up by xss defending, current value is:{}.", text, value);
            return value;
        } else {
            return properties.isTrimText()? StrUtil.trim(text) : text;
        }
    }

}