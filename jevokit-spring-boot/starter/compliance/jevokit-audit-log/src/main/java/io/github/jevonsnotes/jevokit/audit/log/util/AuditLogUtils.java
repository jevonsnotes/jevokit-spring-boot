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
 * 审计日志核心工具：维护线程级审计事件链（ThreadLocal 栈），组装审计 DTO
 * （来源 IP、报文体截断、耗时等）并提交处理器落库/落日志。
 * 使用方式：过滤器（AuditLogFilter）与切面（AuditLogAspect）在前后环节分别调
 * {@link #preHandle(Callback, AuditLogProperties)} / {@link #afterHandle(Callback, AuditLogProperties)}
 * 驱动事件入栈与落盘；{@link #save(String, String, HttpServletRequest, HttpServletResponse, IpConfiguration, AuditLogProcessor, AuditLogProperties, Method)} 负责组装。
 * <p>关键约束（防御性语义）：
 * <ul>
 *   <li>嵌套关联：同一 globalKey 下逐层生成 parent/local 键，clear() 按 parent 回退，支持 forward 嵌套；</li>
 *   <li>来源 IP 取 x-forwarded-for → Proxy-Client-IP → WL-Proxy-Client-IP → remoteAddr 逐级回退
 *       （代理链常见形态，unknown 视为无效继续回退）；</li>
 *   <li>报文体按 content-limit 截断（limit=0 或 ABORT 策略直接丢弃该字段），防止大报文拖垮审计表；</li>
 *   <li>组装/落盘全程 try-catch 静默（仅 debug 日志）——审计失败绝不阻断业务请求。</li>
 * </ul>
 */
@Slf4j(topic = "AUDIT_LOG")
public class AuditLogUtils {
    private static final ThreadLocal<String> SRC_SERVLET_PATH = new ThreadLocal<>();
    private static final ThreadLocal<AuditLogEventDto> EVENTS = new ThreadLocal<>();


    /** 清理线程内的源路径 ThreadLocal（请求结束调用）。 */
    public static void removeSrcServletPath(){
       SRC_SERVLET_PATH.remove();
    }
    /**
     * 获取源路径（forward 场景保留入口路径，使审计归属原始 URL）。
     *
     * @return 源路径；未设置返回 null
     */
    public static String getSrcServletPath(){
       return SRC_SERVLET_PATH.get();
    }
    /**
     * 记录源路径（forward 前设置，后续嵌套审计均归属该路径）。
     *
     * @param path 源路径
     */
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


    /**
     * 取审计用路径：ThreadLocal 已有源路径优先（forward 归因），否则取请求当前 servletPath。
     *
     * @param request 请求；null 返回 null
     * @return 归因路径
     */
    public static String getServletPath(HttpServletRequest request) {
        return request == null ? null : (StrUtil.isEmpty(SRC_SERVLET_PATH.get()) ? request.getServletPath() : SRC_SERVLET_PATH.get());
    }

    /**
     * 审计事件入栈并（around 开启时）落开始审计：首层生成 globalKey，嵌套层继承 globalKey、
     * 以父事件 localKey 为 parentKey 生成新 localKey。
     *
     * @param callback   落审计回调（异常仅记日志，不阻断业务）
     * @param properties 审计配置（enabled-around 决定是否执行回调）
     */
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
    /**
     * 完成审计并回退事件栈：补记 endTime 后执行落审计回调，finally 按 parent 回退 ThreadLocal
     * （回退失败会导致事件残留串到线程池下一请求）。
     *
     * @param callback   落审计回调（异常仅记日志）
     * @param properties 审计配置
     */
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
     * 组装审计 DTO 并提交处理器保存：抓取来源 IP（代理链回退）、报文体（按 content-limit 截断）、
     * 状态/耗时与三级事件键；methodSign 为空表示过滤器链路（抓报文体），否则为切面链路（不抓）。
     *
     * @param eventType 事件类型
     * @param eventName 事件名称（name-mapper 映射，缺省取 eventType）
     * @param request   请求
     * @param response  响应
     * @param ipConfig  本机 IP/端口配置
     * @param processor 审计处理器
     * @param properties 审计配置
     * @param methodSign 触发方法签名；过滤器链路传 null
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
    /**
     * 事件栈回退一层（有 parent 则弹回父事件，否则整体移除）与源路径清理。
     */
    public static void clear() {

        SRC_SERVLET_PATH.remove();
        AuditLogEventDto event = EVENTS.get();
        if(event != null && event.getParent() != null){
            EVENTS.set(event.getParent());
        } else {
            EVENTS.remove();
        }
    }

    /**
     * 判定本次请求是否需要审计：ignore-non-user=true 时占位用户码（未登录）跳过。
     *
     * @param properties 审计配置
     * @param processor  审计处理器（取用户码）
     * @return true 需要审计
     */
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
