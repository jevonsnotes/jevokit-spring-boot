package io.github.jevonsnotes.jevokit.core.http.wrapper;

import cn.hutool.core.lang.caller.CallerUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import io.github.jevonsnotes.jevokit.core.consts.HttpMethod;
import io.github.jevonsnotes.jevokit.core.util.CollectionUtil;
import io.github.jevonsnotes.jevokit.core.util.RequestUtil;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可重复读的请求包装器：构造时一次性把请求体读入字节数组，此后 getInputStream/getReader 可反复获取；
 * 并将 query string 与 POST 表单体合并进参数族方法（getParameter/getParameterValues/getParameterMap），
 * 供过滤器链上多个组件重复消费报文（审计、脱敏、安全清洗等）。
 * <p>关键约束：构造即吞流，仅适合报文较小且确需重复读的过滤器场景；
 * jersey 等会反复取流的调用方命中 SINGLETON_INPUT_STREAM_ONLY_OF_CALLER（按调用类名正则）时
 * 复用同一份缓存流，防止其反复新建流导致内存溢出；bodyBufferingTimeNs 记录吞流耗时供慢请求观测。
 *
 * @deprecated use RequestContentCachingRequestWrapper instead
 */
@Deprecated
@Slf4j
public class RequestReaderHttpServletRequestWrapper extends HttpServletRequestWrapper {
    public static final String TRANSFER_ENCODING = "transfer-encoding";
    public static final String CHUNKED = "chunked";
    private final byte[] body;
    private Map<String, String[]> parameters = null;
    private Charset charset = null;
    private final HttpServletRequest request;
    private final static HashMap<String, String[]> EMPTY_MAP = new HashMap<>();
    public static List<String> SINGLETON_INPUT_STREAM_ONLY_OF_CALLER = CollectionUtil.toList("org.glassfish.jersey.innate.io.InputStreamWrapper.*");
    /**
     * 缓存输入流，用于解决部分反复调用getInputStream函数的wrapper，避免造成读写内存溢出问题
     */
    private final Map<String, ByteArrayInputStream> SINGLETON_INPUT_STREAM = new ConcurrentHashMap<>();
    @Getter
    private long bodyBufferingTimeNs = 0;


    /**
     * 构造并缓存请求体（一次性读空原始输入流）
     *
     * @param request 原始请求
     * @throws IOException 读取请求体失败
     */
    public RequestReaderHttpServletRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        long bufferStartTime = System.nanoTime();
        this.body = IOUtils.toByteArray(request.getInputStream());
        bodyBufferingTimeNs = System.nanoTime() - bufferStartTime;
        this.request = request;
    }

    /**
     * 获取字符读取器（基于缓存的请求体，可重复获取）
     *
     * @return 缓冲字符流
     * @throws IOException 不会实际抛出（读的是内存缓冲）
     */
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(this.getInputStream()));
    }

    /**
     * 获取字节输入流（基于缓存的请求体，可重复获取；jersey 类调用方走单例缓存流）
     *
     * @return 包装缓存体的 ServletInputStream（isFinished/isReady 恒为 false）
     * @throws IOException 不会实际抛出（读的是内存缓冲）
     */
    public ServletInputStream getInputStream() throws IOException {

        Optional<String> caller = singletonInputStreamSupportOnlyOfCaller();
        ByteArrayInputStream bais = caller.map(s -> SINGLETON_INPUT_STREAM.computeIfAbsent(s, k -> createByteArrayInputStream())).orElseGet(this::createByteArrayInputStream);
        return new ServletInputStream() {
            public int read() {
                return bais.read();
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
     * 基于缓存请求体新建字节流
     *
     * @return 缓冲体包装流
     */
    private ByteArrayInputStream createByteArrayInputStream() {
        return new ByteArrayInputStream(body);
    }


    /**
     * 取参数（query string 与 POST 表单体合并视图）
     *
     * @param name 参数名
     * @return 首个匹配值；无匹配返回 null
     */
    @Override
    public String getParameter(String name) {
        try {
            parseRequest();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse the request!", e);
        }
        if (parameters == null) return null;
        String[] values = parameters.get(name);
        if (values == null || values.length == 0)
            return null;
        return values[0];
    }


    /**
     * 取缓存的请求体副本
     *
     * @return 请求体字节数组（内部数组的同一引用，勿原地修改）
     */
    public byte[] toByteArray() {
        return this.body;
    }


    /**
     * 解析并缓存参数（query string + urlencoded 表单体，首次调用后短路）
     *
     * @throws IOException 理论不会抛出（读的是已缓存内容）
     */
    private void parseRequest() throws IOException {
        if (parameters != null)
            return; //already parsed

        HashMap<String, List<String>> mapA = new HashMap<>();
        List<String> list;
        //不能直接用hutool的 httputil，因为会自动截取path导致参数缺失
        Map<String, List<String>> query = RequestUtil.decodeParams(((HttpServletRequest) this.getRequest()).getQueryString(), getCharset());
        if (query != null) {
            for (String key : query.keySet()) {
                list = query.get(key);
                mapA.put(key, list);
            }
        }

        if (shouldBufferBody()) {

            try {
                log.debug("Length of contentData byte array = {}", body.length);
                if (request.getContentLength() != body.length) {
                    log.warn("Content-length different from byte array length! cl={}, array={}", request.getContentLength(), body.length);
                }
            } catch (Exception e) {
                log.error("Error checking if request body gzipped!", e);
            }


            // only does magic body param parsing for POST form bodies
            if (isFormPost()) {
                String enc = getCharset().name();

                String s = new String(body, enc), name, value;
                StringTokenizer st = new StringTokenizer(s, "&");
                int i;

                boolean decode = request.getContentType() != null;
                while (st.hasMoreTokens()) {
                    s = st.nextToken();
                    i = s.indexOf("=");
                    if (i > 0 && s.length() > i + 1) {
                        name = s.substring(0, i);
                        value = s.substring(i + 1);
                        if (decode) {
                            try {
                                name = URLDecoder.decode(name, StandardCharsets.UTF_8.name());
                            } catch (Exception ignored) {
                            }
                            try {
                                value = URLDecoder.decode(value, StandardCharsets.UTF_8.name());
                            } catch (Exception ignored) {
                            }
                        }
                        list = mapA.computeIfAbsent(name, k -> new LinkedList<>());
                        list.add(value);
                    }
                }
            }
        }

        HashMap<String, String[]> map = new HashMap<>(mapA.size() * 2);
        for (String key : mapA.keySet()) {
            list = mapA.get(key);
            map.put(key, list.toArray(new String[0]));
        }
        parameters = map;
    }

    /**
     * 取请求字符集（未声明或解析失败时兜底 UTF-8，不会返回 null）
     *
     * @return 解析出的 Charset
     */
    private Charset getCharset() {

        try {
            if (charset == null) {
                String characterEncoding = getCharacterEncoding();
                if (characterEncoding != null) {
                    charset = Charset.forName(characterEncoding);
                }
            }
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            // 非法或未知字符集名：忽略，charset 保持 null 走下方兜底
        }
        if (charset != null) {
            return charset;
        }
        // 按 UTF-8 兜底系 Tomcat 10.1（Servlet 6.0）DEFAULT_BODY_CHARSET 语义延续
        return StandardCharsets.UTF_8;
    }


    /**
     * 判断请求是否携带需要缓冲的请求体（有 Content-Length 或 chunked 传输）
     *
     * @return 需要缓冲返回 true
     */
    private boolean shouldBufferBody() {

        if (log.isDebugEnabled()) {
            log.debug("Path = {}", request.getPathInfo());
            log.debug("Content-Length header = {}", request.getContentLength());
        }

        boolean should = false;
        if (request.getContentLength() > 0) {
            should = true;
        } else if (request.getContentLength() == -1) {
            final String transferEncoding = request.getHeader(TRANSFER_ENCODING);
            if (transferEncoding != null && transferEncoding.equals(CHUNKED)) {
                should = true;
            }
        }

        return should;
    }

    /**
     * 取参数名枚举（触发参数解析，合并 query 与表单体）
     *
     * @return 全部参数名的枚举视图
     */
    @Override
    public Enumeration<String> getParameterNames() {
        try {
            parseRequest();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse the request!", e);
        }
        return new Enumeration<String>() {
            private final String[] arr = getParameters().keySet().toArray(new String[0]);
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
     * 取参数表（值数组已逐项 clone，修改返回值不影响内部状态）
     *
     * @return 参数名到值数组的 Map；尚未解析过（无参数请求）时返回空 Map（非 null）
     */
    public HashMap<String, String[]> getParameters() {
        if (parameters == null) return EMPTY_MAP;
        HashMap<String, String[]> map = new HashMap<String, String[]>(parameters.size() * 2);
        for (String key : parameters.keySet()) {
            map.put(key, parameters.get(key).clone());
        }
        return map;
    }

    /**
     * 取参数值数组（返回内部数组的 clone，修改不影响后续读取）
     *
     * @param name 参数名
     * @return 全部匹配值；无匹配返回 null
     */
    @Override
    public String[] getParameterValues(String name) {
        try {
            parseRequest();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse the request!", e);
        }
        if (parameters == null) return null;
        String[] arr = parameters.get(name);
        if (arr == null)
            return null;
        return arr.clone();
    }

    /**
     * 取参数表（等价 {@link #getParameters()}，值为逐项 clone 的副本）
     *
     * @return 参数名到值数组的 Map
     */
    @Override
    public Map<String, String[]> getParameterMap() {
        try {
            parseRequest();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse the request!", e);
        }
        return getParameters();
    }

    /**
     * 判断caller是否只支持单例输入流
     * 调用深度：singletonInputStreamSupportOnlyOfCaller→getInputStream→外部
     *
     * @return 命中 SINGLETON_INPUT_STREAM_ONLY_OF_CALLER 正则的调用方全限定名；未命中返回空 Optional
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
     * 判断是否为 POST 的 urlencoded 表单请求（此类型的表单体需要并入参数解析）
     *
     * @return Content-Type 含 application/x-www-form-urlencoded 且方法为 POST 时 true
     */
    private boolean isFormPost() {
        String contentType = this.getContentType();
        return contentType != null && contentType.contains("application/x-www-form-urlencoded") && HttpMethod.POST.matches(this.getMethod());
    }
}