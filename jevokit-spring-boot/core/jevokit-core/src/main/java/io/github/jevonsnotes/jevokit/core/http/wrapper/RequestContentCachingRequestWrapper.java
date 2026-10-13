package io.github.jevonsnotes.jevokit.core.http.wrapper;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.caller.CallerUtil;
import cn.hutool.core.util.StrUtil;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.ContentCachingRequestWrapper;
import io.github.jevonsnotes.jevokit.core.consts.HttpMethod;
import io.github.jevonsnotes.jevokit.core.util.CollectionUtil;
import io.github.jevonsnotes.jevokit.core.util.RequestUtil;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存请求内容
 * 1、解决重复读取内容的问题
 * 2、解决上传文件getParts为空的问题
 */

@Slf4j
public class RequestContentCachingRequestWrapper extends ContentCachingRequestWrapper {
    private Charset charset = null;
    private Map<String, String[]> parameters = null;
    public static List<String> SINGLETON_INPUT_STREAM_ONLY_OF_CALLER = CollectionUtil.toList("org.glassfish.jersey.innate.io.InputStreamWrapper.*");
    /**
     * 缓存输入流，用于解决部分反复调用getInputStream函数的wrapper，避免造成读写内存溢出问题
     */
    private final Map<String, ServletInputStream> SINGLETON_INPUT_STREAM = new ConcurrentHashMap<>();

    /**
     * 构造并预读请求体进缓存（multipart 表单除外——预读会导致 getParts 为空）
     *
     * @param request 原始请求
     */
    public RequestContentCachingRequestWrapper(HttpServletRequest request) {
        super(request);
        if(!isMultiPartFormPost()){
            forceReadToCacheIfFirst();
        }
    }

    /**
     * 构造并预读请求体进缓存（限定缓存上限）
     *
     * @param request           原始请求
     * @param contentCacheLimit 缓存最大字节数（超出的请求体不进缓存，参照 Spring 同名参数语义）
     */
    public RequestContentCachingRequestWrapper(HttpServletRequest request, int contentCacheLimit) {
        super(request, contentCacheLimit);
        if(!isMultiPartFormPost()){
            forceReadToCacheIfFirst();
        }
    }

    /**
     * 获取输入流：multipart 透传原始流（预读会致 getParts 为空），其余返回基于缓存的新流
     * （jersey 类调用方复用同一份缓存流，防其反复取流导致内存溢出）
     *
     * @return 可重复读的 ServletInputStream
     * @throws IOException 透传原始流时可能抛出
     */
    @Override
    public ServletInputStream getInputStream() throws IOException {
        // MultiPart请求只能获取单例，已读的输入流会导致getParts为空
        if(isMultiPartFormPost()){
            return super.getInputStream();
        } else {
            // 针对特殊key只能获取单例输入流，不能重复读
            Optional<String> caller = singletonInputStreamSupportOnlyOfCaller();
            if(caller.isPresent()){
                return  caller.map(s -> SINGLETON_INPUT_STREAM.computeIfAbsent(s, k -> getCacheInputStream())).orElseThrow(() -> new RuntimeException("can not obtain input stream"));
            } else {
                // 获取一个新的缓存输入流
                return getCacheInputStream();
            }
        }
    }

    /**
     * 构造时预读请求体进缓存（读到文件尾即可，内容留存于父类 ContentCachingRequestWrapper）
     */
    private void forceReadToCacheIfFirst(){
        try {
            IoUtil.read(super.getInputStream());
        } catch (IOException e) {
            throw new IORuntimeException(e);
        }
    }


    /**
     * 基于缓存内容新建可重复读的输入流（每次调用新实例，读互不干扰）
     *
     * @return 包装缓存快照的 ServletInputStream（isFinished/isReady 恒为 false）
     */
    public ServletInputStream getCacheInputStream() {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(getContentAsByteArray());
        return new ServletInputStream() {
            public int read() {
                return byteArrayInputStream.read();
            }

            public boolean isFinished() {
                return false;
            }

            public boolean isReady() {
                return false;
            }

            public void setReadListener(ReadListener readListener) {
            }
        };
    }

    /**
     * 取参数（multipart 走容器原生，其余走 query + 表单体缓存的合并解析）
     *
     * @param name 参数名
     * @return 首个匹配值；无匹配返回 null
     */
    @Override
    public String getParameter(String name) {
        String[] param = getParameterMap().get(name);
        return param != null && param.length > 0 ? param[0] : null;
    }

    /**
     * 取参数表（multipart 走容器原生，其余走 query + 表单体缓存的合并解析）
     *
     * @return 参数名到值数组的 Map
     */
    @Override
    public Map<String, String[]> getParameterMap() {
        if(isMultiPartFormPost()){
            return super.getParameterMap();
        } else {
            parseBody();
            return parameters;
        }
    }

    /**
     * 取参数名枚举（匿名 Enumeration 视图，遍历 getParameterMap 的键）
     *
     * @return 全部参数名的枚举
     */
    @Override
    public Enumeration<String> getParameterNames() {
        return new Enumeration<String>() {
            private final String[] arr = getParameterMap().keySet().toArray(new String[0]);
            private int idx = 0;

            @Override
            public boolean hasMoreElements() {
                return idx < arr.length;
            }

            @Override
            public String nextElement() {
                return arr[idx++];
            }

        };
    }

    /**
     * 取参数值数组（返回内部数组的 clone，修改不影响后续读取）
     *
     * @param name 参数名
     * @return 全部匹配值；无匹配返回 null
     */
    @Override
    public String[] getParameterValues(String name) {
        String[] param = getParameterMap().get(name);
        return param == null ? null : param.clone();
    }

    /**
     * 判断是否为 multipart 文件上传请求（此类型不缓存、参数走容器原生解析）
     *
     * @return Content-Type 含 multipart/form-data 且方法为 POST 时 true
     */
    public boolean isMultiPartFormPost() {
        String contentType = this.getContentType();
        return contentType != null && contentType.contains("multipart/form-data") && HttpMethod.POST.matches(this.getMethod());
    }
    /**
     * 判断是否为 POST 的 urlencoded 表单请求（此类型的表单体需要并入参数解析）
     *
     * @return Content-Type 含 application/x-www-form-urlencoded 且方法为 POST 时 true
     */
    private boolean isFormPost() {
        String contentType = this.getContentType();
        return contentType != null && contentType.contains("application/x-www-form-urlencoded") && org.springframework.http.HttpMethod.POST.matches(this.getMethod());
    }

    /**
     * 取请求字符集（未声明时兜底 UTF-8）
     *
     * @return 字符集名，不会返回 null
     */
    @Override
    public String getCharacterEncoding() {
        return getCharset().name();
    }

    /**
     * 判断当前输入流调用方是否只支持单例流（按调用栈第 4 层类名匹配 SINGLETON_INPUT_STREAM_ONLY_OF_CALLER 正则）
     *
     * @return 命中的调用方全限定名；未命中返回空 Optional
     */
    public Optional<String> singletonInputStreamSupportOnlyOfCaller() {

        // 暂时写死层级4 用于解决jersey反复获取新的输入流并调用read方法导致内存溢出问题 InputStreamWrapper.getWrappedIOE().read()
        // singletonInputStreamSupportOnlyOfCaller
        // →getInputStream
        // →org.glassfish.jersey.servlet.WebComponent
        // →org.glassfish.jersey.innate.io.InputStreamWrapper
        String callerName = CallerUtil.getCaller(4).getName();
        return SINGLETON_INPUT_STREAM_ONLY_OF_CALLER
                .stream()
                .map((pattern) -> callerName.matches(pattern) ? callerName : null)
                .filter(StrUtil::isNotBlank)
                .findFirst();
    }

    /**
     * 解析并缓存参数（query string + urlencoded 表单体，首次调用后短路）
     */
    private void parseBody(){
        if( parameters != null ){ return;}

        Map<String, List<String>> tempParams = new HashMap<>();
        //不能直接用hutool的 httputil，因为会自动截取path导致参数缺失
        Map<String, List<String>> query = RequestUtil.decodeParams(((HttpServletRequest) this.getRequest()).getQueryString(), getCharset());
        if (query != null) {
            for (String key : query.keySet()) {
                List<String> list = query.get(key);
                tempParams.put(key, list);
            }
        }

        if(isFormPost()){
            String s = getContentAsString(), name, value;
            StringTokenizer st = new StringTokenizer(s, "&");
            int i;

            while (st.hasMoreTokens()) {
                s = st.nextToken();
                i = s.indexOf("=");
                if (i > 0 && s.length() > i + 1) {
                    name = s.substring(0, i);
                    value = s.substring(i + 1);
                    try {
                        name = URLDecoder.decode(name, StandardCharsets.UTF_8.name());
                    } catch (Exception ignored) {
                    }
                    try {
                        value = URLDecoder.decode(value, StandardCharsets.UTF_8.name());
                    } catch (Exception ignored) {
                    }
                    List<String> list = tempParams.computeIfAbsent(name, k -> new LinkedList<>());
                    list.add(value);
                }
            }
        }

        HashMap<String, String[]> map = new HashMap<>(tempParams.size() * 2);
        for (String key : tempParams.keySet()) {
            map.put(key, tempParams.get(key).toArray(new String[0]));
        }
        parameters = map;
    }

    /**
     * 以字符串返回缓存的请求体：按请求字符集解码缓存字节，未声明/非法字符集时兜底 UTF-8
     * （字符集语义与 {@code parseBody} 的表单体解析一致）
     *
     * @return 缓存请求体解码后的字符串
     */
    public String getContentAsString() {
        Charset bodyCharset = getCharset() != null ? getCharset() : StandardCharsets.UTF_8;
        return new String(getContentAsByteArray(), bodyCharset);
    }

    /**
     * 取请求字符集（未声明时兜底 UTF-8，解析失败回退 null 由调用方处理）
     *
     * @return 解析出的 Charset
     */
    private Charset getCharset() {
        try {
            if (charset == null) {
                String enc = getRequest().getCharacterEncoding();
                enc = enc == null ? "UTF-8" : enc;
                charset = Charset.forName(enc);
            }
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            // 非法或未知字符集名：忽略，charset 保持 null 由调用方兜底
        }
        return charset;
    }
}