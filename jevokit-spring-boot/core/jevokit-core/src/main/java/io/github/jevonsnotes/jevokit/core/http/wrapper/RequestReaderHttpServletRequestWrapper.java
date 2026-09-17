package io.github.jevonsnotes.jevokit.core.http.wrapper;

import cn.hutool.core.lang.caller.CallerUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.tomcat.util.buf.B2CConverter;
import io.github.jevonsnotes.jevokit.core.consts.HttpMethod;
import io.github.jevonsnotes.jevokit.core.util.CollectionUtil;
import io.github.jevonsnotes.jevokit.core.util.RequestUtil;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description TODO
 * @Date 2024/12/3
 * @Version 0.0.1
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


    public RequestReaderHttpServletRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        long bufferStartTime = System.nanoTime();
        this.body = IOUtils.toByteArray(request.getInputStream());
        bodyBufferingTimeNs = System.nanoTime() - bufferStartTime;
        this.request = request;
    }

    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(this.getInputStream()));
    }

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

    private ByteArrayInputStream createByteArrayInputStream() {
        return new ByteArrayInputStream(body);
    }


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


    public byte[] toByteArray() {
        return this.body;
    }


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
                                name = URLDecoder.decode(name, StandardCharsets.UTF_8);
                            } catch (Exception ignored) {
                            }
                            try {
                                value = URLDecoder.decode(value, StandardCharsets.UTF_8);
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

    private Charset getCharset() {

        try {
            if (charset == null) {
                String characterEncoding = getCharacterEncoding();
                if (characterEncoding != null) {
                    charset = B2CConverter.getCharset(characterEncoding);
                }
            }
        } catch (UnsupportedEncodingException e) {
            // Ignore
        }
        if (charset != null) {
            return charset;
        }
        return org.apache.coyote.Constants.DEFAULT_BODY_CHARSET;
    }


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

    public HashMap<String, String[]> getParameters() {
        if (parameters == null) return EMPTY_MAP;
        HashMap<String, String[]> map = new HashMap<String, String[]>(parameters.size() * 2);
        for (String key : parameters.keySet()) {
            map.put(key, parameters.get(key).clone());
        }
        return map;
    }

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
     * @return 返回模式
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

    private boolean isFormPost() {
        String contentType = this.getContentType();
        return contentType != null && contentType.contains("application/x-www-form-urlencoded") && HttpMethod.POST.matches(this.getMethod());
    }
}