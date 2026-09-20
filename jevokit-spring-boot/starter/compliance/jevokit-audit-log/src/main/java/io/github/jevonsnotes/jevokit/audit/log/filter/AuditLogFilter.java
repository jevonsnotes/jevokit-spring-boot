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
 * 审计日志过滤器：URL 驱动的自动审计入口——按 HTTP 方法 + Ant 路径规则
 * （spkit.audit-log.event-types.patterns）识别需审计的请求，包装请求/响应以捕获报文体并落审计。
 * 使用方式：由 JevokitAuditLogConfiguration 以 {@code /*}、order=200 注册，无需手工配置；
 * 匹配到的请求经 preHandle/afterHandle 两次回调（around 开启时落两条，否则仅完成后一条）。
 * <p>关键约束：未命中规则的请求只做请求包装直通（不捕获响应体，零开销降级）；
 * 请求体经 RequestContentCachingRequestWrapper 缓存，响应体经 ResponseReaderHttpServletResponseWrapper
 * 读取——均为只读快照，不影响业务流；ignore-non-user=true 时默认用户（DEF_USER_CODE）的请求跳过审计。
 */
@RequiredArgsConstructor
public class AuditLogFilter implements Filter {
    AntPathMatcher antPathMatcher = new AntPathMatcher();
    private final AuditLogProcessor processor;
    private final AuditLogProperties properties;
    private final IpConfiguration ipConfig;

    /**
     * URL 驱动的审计入口：命中事件规则的请求包装请求/响应体，preHandle/afterHandle 两个时机落审计
     * （around 开启时两次、否则仅完成后一次）；未命中/未开启直通。
     * <p>清理兜底：{@code chain.doFilter} 包于 try/finally，afterHandle 在 finally 执行——
     * 下游抛异常（IOException/ServletException/RuntimeException 等）时事件链（EVENTS）与
     * 源路径（SRC_SERVLET_PATH）ThreadLocal 仍被清理（不随线程池复用串到下一请求），异常原样向上透传。
     *
     * @throws IOException 由后续过滤链透传（审计本身不产生异常；透传前 finally 已完成 ThreadLocal 清理）
     * @throws ServletException 由后续过滤链透传（审计本身不产生异常；透传前 finally 已完成 ThreadLocal 清理）
     */
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
                    try {
                        chain.doFilter(requestWrapper, responseWrapper);
                    } finally {
                        //下游异常时也必须走到 afterHandle：其内部 finally 的 clear() 负责回退事件链/清理源路径
                        AuditLogUtils.afterHandle(()->{
                            AuditLogUtils.save(eventType,eventType,requestWrapper, responseWrapper,ipConfig, processor,properties,null);
                        },properties);
                    }
                }
            } else {
                chain.doFilter(requestWrapper, response);
            }
        } else {
            chain.doFilter(request, response);
        }
    }


    /**
     * 事件类型识别：按 HTTP 方法分组匹配 event-types.patterns 的 Ant 路径规则（遍历首个命中即返回）。
     *
     * @param request 请求
     * @return 命中的事件类型；未命中返回 null（该请求不做审计）
     */
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
