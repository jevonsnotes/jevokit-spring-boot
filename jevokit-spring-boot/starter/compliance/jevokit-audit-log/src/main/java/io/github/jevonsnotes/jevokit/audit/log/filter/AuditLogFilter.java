package io.github.jevonsnotes.jevokit.audit.log.filter;

import cn.hutool.extra.spring.SpringUtil;
import jakarta.servlet.http.Part;
import lombok.RequiredArgsConstructor;
import org.springframework.util.AntPathMatcher;
import io.github.jevonsnotes.jevokit.audit.log.processor.AuditLogProcessor;
import io.github.jevonsnotes.jevokit.audit.log.util.AuditLogUtils;
import io.github.jevonsnotes.jevokit.configuration.audit.log.AuditLogProperties;
import io.github.jevonsnotes.jevokit.configuration.config.IpConfiguration;
import io.github.jevonsnotes.jevokit.core.consts.HttpMethod;
import io.github.jevonsnotes.jevokit.core.http.wrapper.RequestContentCachingRequestWrapper;
import io.github.jevonsnotes.jevokit.core.http.wrapper.ResponseReaderHttpServletResponseWrapper;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @Description TODO
 * @Date 2024/12/3
 * @Version 0.0.1
 */
@RequiredArgsConstructor
public class AuditLogFilter implements Filter {
    AntPathMatcher antPathMatcher = new AntPathMatcher();
    private final AuditLogProcessor processor;
    private final AuditLogProperties properties;
    private final IpConfiguration ipConfig;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if(request instanceof HttpServletRequest
                && properties.isEnabled()
                && AuditLogUtils.needToPrecess(properties, processor)) {
            HttpServletRequest requestWrapper = new RequestContentCachingRequestWrapper((HttpServletRequest) request);

            String eventType = getEventType(requestWrapper);
            if(eventType != null) {
                if(response instanceof HttpServletResponse) {
                    HttpServletResponse responseWrapper = new ResponseReaderHttpServletResponseWrapper((HttpServletResponse) response);

                    AuditLogUtils.preHandle(()->{
                        AuditLogUtils.save(eventType,eventType,requestWrapper, responseWrapper,ipConfig, processor,properties,null);
                    },properties);
                    chain.doFilter(requestWrapper, responseWrapper);
                    AuditLogUtils.afterHandle(()->{
                        AuditLogUtils.save(eventType,eventType,requestWrapper, responseWrapper,ipConfig, processor,properties,null);
                    },properties);
                }
            } else {
                chain.doFilter(requestWrapper, response);
            }
        } else {
            chain.doFilter(request, response);
        }
    }


    private String getEventType(HttpServletRequest request) {
        AuditLogProperties properties = SpringUtil.getBean(AuditLogProperties.class);
        Map<HttpMethod, Map<String, List<String>>> eventTypesPatterns = properties.getEventTypes().getPatterns();
        if (eventTypesPatterns != null) {
            for (Map.Entry<HttpMethod, Map<String, List<String>>> entry : eventTypesPatterns.entrySet()) {
                String method = entry.getKey().name();
                if (request.getMethod().equalsIgnoreCase(method)) {
                    Map<String, List<String>> events = entry.getValue();
                    if (events != null) {
                        for (Map.Entry<String, List<String>> entryInner : events.entrySet()) {
                            String eventType = entryInner.getKey();
                            List<String> patterns = entryInner.getValue();
                            if (patterns != null) {
                                for (String pattern : patterns) {
                                    if (antPathMatcher.match(pattern, AuditLogUtils.getServletPath(request))) {
                                        return eventType;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return  null;
    }
}
