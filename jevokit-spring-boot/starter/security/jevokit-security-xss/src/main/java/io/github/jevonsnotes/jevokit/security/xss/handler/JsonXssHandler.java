package io.github.jevonsnotes.jevokit.security.xss.handler;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.jevonsnotes.jevokit.configuration.holder.ConfigHolder;
import io.github.jevonsnotes.jevokit.security.xss.cleaner.XssCleaner;
import io.github.jevonsnotes.jevokit.configuration.security.xss.XssProperties;

import java.io.IOException;
/**
 * json提交处理器
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