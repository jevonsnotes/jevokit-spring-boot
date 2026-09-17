package io.github.jevonsnotes.jevokit.audit.log.util;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.reflect.MethodSignature;
import io.github.jevonsnotes.jevokit.audit.log.dto.AuditLogDto;
import io.github.jevonsnotes.jevokit.audit.log.dto.AuditLogEventDto;
import io.github.jevonsnotes.jevokit.audit.log.processor.AuditLogProcessor;
import io.github.jevonsnotes.jevokit.audit.log.processor.DefaultAuditLogProcessor;
import io.github.jevonsnotes.jevokit.configuration.audit.log.AuditLogContentLimitProperties;
import io.github.jevonsnotes.jevokit.configuration.audit.log.AuditLogProperties;
import io.github.jevonsnotes.jevokit.configuration.config.IpConfiguration;
import io.github.jevonsnotes.jevokit.core.http.wrapper.RequestContentCachingRequestWrapper;
import io.github.jevonsnotes.jevokit.core.http.wrapper.RequestReaderHttpServletRequestWrapper;
import io.github.jevonsnotes.jevokit.core.http.wrapper.ResponseReaderHttpServletResponseWrapper;
import io.github.jevonsnotes.jevokit.core.lang.Callback;

import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletResponseWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * @Description TODO
 * @Date 2024/6/28 9:16
 * @Version 0.0.1
 */
@Slf4j(topic = "AUDIT_LOG")
public class AuditLogUtils {
    private static final ThreadLocal<String> SRC_SERVLET_PATH = new ThreadLocal<>();
    private static final ThreadLocal<AuditLogEventDto> EVENTS = new ThreadLocal<>();


    public static void removeSrcServletPath(){
       SRC_SERVLET_PATH.remove();
    }
    public static String getSrcServletPath(){
       return SRC_SERVLET_PATH.get();
    }
    public static void setSrcServletPath(String path){
        SRC_SERVLET_PATH.set(path);
    }

    private static String getRemoteIpAddr(HttpServletRequest request) {
        if(request == null){
            return null;
        }
        String ip = request.getHeader("x-forwarded-for");
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }


    public static String getServletPath(HttpServletRequest request) {
        return request == null ? null : (StrUtil.isEmpty(SRC_SERVLET_PATH.get()) ? request.getServletPath() : SRC_SERVLET_PATH.get());
    }

    public static void preHandle(Callback callback,AuditLogProperties properties){
        if(EVENTS.get() == null){
            EVENTS.set(AuditLogEventDto
                    .builder()
                    .globalKey(UUID.randomUUID(true).toString(true))
                    .parentKey(UUID.randomUUID(true).toString(true))
                    .localKey(UUID.randomUUID(true).toString(true))
                    .startTime(DateUtil.date())
                    .build());
        } else {
            EVENTS.set(AuditLogEventDto
                    .builder()
                    .globalKey(EVENTS.get().getGlobalKey())
                    .parentKey(EVENTS.get().getLocalKey())
                    .localKey(UUID.randomUUID(true).toString(true))
                    .startTime(DateUtil.date())
                    .parent(EVENTS.get())
                    .build());
        }

        if(properties.isEnabledAround()){
            try {
                callback.call();
            } catch (Exception e) {
                log.error(e.getMessage(),e);//输出日志，不阻断
            }
        }
    }
    public static void afterHandle(Callback callback,AuditLogProperties properties){
        AuditLogEventDto event = EVENTS.get();
        if(event != null){
            event.setEndTime(DateUtil.date());
        }
        try {
            callback.call();
        } catch (Exception e){
            log.error(e.getMessage(),e);//输出日志，不阻断
        } finally {
            clear();
        }
    }

    /**
     * 封装审计日志，并调用保存方法
     *
     * @param eventType
     * @param request
     * @param response
     */
    public static void save(
            String eventType, String eventName, HttpServletRequest request,
            HttpServletResponse response, IpConfiguration ipConfig, AuditLogProcessor processor,
            AuditLogProperties properties, Method methodSign) {
        try {
            String contentType = request == null ? StrUtil.EMPTY : request.getContentType();
            String scheme = request == null ? StrUtil.EMPTY : request.getScheme();
            String method = request == null ? StrUtil.EMPTY  : request.getMethod();
            String classMethod = methodSign == null ? StrUtil.EMPTY : methodSign.getName() ;
            String remoteAddress = StrUtil.isEmpty(remoteAddress = getRemoteIpAddr(request))? StrUtil.EMPTY : remoteAddress;
            String servletPath = StrUtil.isEmpty(servletPath = getServletPath(request))? StrUtil.EMPTY  : servletPath;
            String className = methodSign == null ? StrUtil.EMPTY : methodSign.getDeclaringClass().getName();
            Integer status = (EVENTS.get().getEndTime() == null || response == null) ? null : response.getStatus();
            //封装审计日志
            AuditLogDto.AuditLogDtoBuilder builder = AuditLogDto.builder()
                    .globalKey(EVENTS.get().getGlobalKey())
                    .parentKey(EVENTS.get().getParentKey())
                    .localKey(EVENTS.get().getLocalKey())
                    .contentType(contentType)
                    .sourceIp(remoteAddress)
                    .localIp(ipConfig.getIp())
                    .port(ipConfig.getPort())
                    .servletPath(servletPath)
                    .scheme(scheme)
                    .method(method)
                    .classMethod(classMethod)
                    .className(className)
                    .comCode(processor.getComCode())
                    .userCode(processor.getUserCode())
                    .eventType(eventType)
                    .eventName(Optional.ofNullable(properties.getEventTypes().getNameMapper().get(eventType)).orElse(eventType))
                    .status(status)
                    .startTime(EVENTS.get().getStartTime())
                    .endTime(EVENTS.get().getEndTime())
                    .cost(EVENTS.get().getEndTime() == null ? null : EVENTS.get().getEndTime().getTime() - EVENTS.get().getStartTime().getTime());

            //方法签名为空，说明是过滤器调用
            if(methodSign == null){

                //封装请求参数和请求体
                if(isRequestWrapper(request)){
                    if (request.getQueryString() != null) {
                        builder.queryString(
                                processContentLimit(
                                        request.getQueryString(),
                                        properties.getContentLimit().getQueryString(),
                                        properties.getContentLimit().getStrategy())
                        );
                    }
                    RequestContentCachingRequestWrapper wrapper = (RequestContentCachingRequestWrapper) request;
                    byte[] byteArray = wrapper.getContentAsByteArray();
                    if(byteArray.length > 0){
                        builder.requestBody(
                                processContentLimit(
                                        new String(byteArray),
                                        properties.getContentLimit().getRequestBody(),
                                        properties.getContentLimit().getStrategy())
                        );
                    }
                }

                //封装返回体
                if(isResponseWrapper(response)){
                    ResponseReaderHttpServletResponseWrapper wrapper = (ResponseReaderHttpServletResponseWrapper) response;
                    byte[] byteArray = wrapper.toByteArray();
                    if(byteArray != null && byteArray.length > 0){
                        builder.responseBody(processContentLimit(
                                new String(byteArray),
                                properties.getContentLimit().getResponseBody(),
                                properties.getContentLimit().getStrategy()));
                    }
                }
            }

            AuditLogDto dto = builder.build();

            //是否植入mdc
            if (properties.isEnabledMdc()) {
                processor.mdc(dto);
            }

            //记录审计日志
            processor.save(dto);

            //清除mdc
            if (properties.isEnabledMdc()) {
                processor.removeMdc(dto);
            }
        } catch (Exception e) {
            //静默处理
            log.debug(e.getMessage(),e);
        }
    }

    private static String processContentLimit(String content , Integer limit,AuditLogContentLimitProperties.Strategy strategy){
        if( limit > -1 && content.length() > limit){
            if(limit == 0 || AuditLogContentLimitProperties.Strategy.ABORT.equals(strategy)){
                content = null;
            } else {
                content =  content.substring(0, limit);
            }
        }
        return content;
    }
    public static void clear() {

        SRC_SERVLET_PATH.remove();
        AuditLogEventDto event = EVENTS.get();
        if(event != null && event.getParent() != null){
            EVENTS.set(event.getParent());
        } else {
            EVENTS.remove();
        }
    }

    public static boolean needToPrecess(AuditLogProperties properties,AuditLogProcessor processor)  {
        return !properties.isIgnoreNonUser() || (StrUtil.isNotBlank(processor.getUserCode())
                && !DefaultAuditLogProcessor.DEF_USER_CODE.equals(processor.getUserCode()));
    }

    private static boolean isRequestWrapper(HttpServletRequest request) {
        if(request instanceof ServletRequestWrapper){
            if(request instanceof RequestContentCachingRequestWrapper){
                return true;
            } else {
                return isRequestWrapper((HttpServletRequest) ((ServletRequestWrapper)request).getRequest());
            }
        }
        return false;
    }
    private static boolean isResponseWrapper(HttpServletResponse response) {
        if(response instanceof ServletResponseWrapper){
            if(response instanceof ResponseReaderHttpServletResponseWrapper){
                return true;
            } else {
                return isResponseWrapper((HttpServletResponse) ((ServletResponseWrapper)response).getResponse());
            }
        }
        return false;
    }

}
