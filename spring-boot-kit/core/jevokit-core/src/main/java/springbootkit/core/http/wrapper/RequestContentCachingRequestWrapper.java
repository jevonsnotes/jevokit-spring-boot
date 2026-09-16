package springbootkit.core.http.wrapper;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.caller.CallerUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.buf.B2CConverter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import springbootkit.core.consts.HttpMethod;
import springbootkit.core.util.CollectionUtil;
import springbootkit.core.util.RequestUtil;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description 缓存请求内容
 * 1、解决重复读取内容的问题
 * 2、解决上传文件getParts为空的问题
 * @Date 2024/12/3
 * @Version 0.0.1
 *
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

    public RequestContentCachingRequestWrapper(HttpServletRequest request) {
        super(request);
        if(!isMultiPartFormPost()){
            forceReadToCacheIfFirst();
        }
    }

    public RequestContentCachingRequestWrapper(HttpServletRequest request, int contentCacheLimit) {
        super(request, contentCacheLimit);
        if(!isMultiPartFormPost()){
            forceReadToCacheIfFirst();
        }
    }

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

    private void forceReadToCacheIfFirst(){
        try {
            IoUtil.read(super.getInputStream());
        } catch (IOException e) {
            throw new IORuntimeException(e);
        }
    }


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

    @Override
    public String getParameter(String name) {
        String[] param = getParameterMap().get(name);
        return param != null && param.length > 0 ? param[0] : null;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        if(isMultiPartFormPost()){
            return super.getParameterMap();
        } else {
            parseBody();
            return parameters;
        }
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return new Enumeration<>() {
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

    @Override
    public String[] getParameterValues(String name) {
        String[] param = getParameterMap().get(name);
        return param == null ? null : param.clone();
    }

    public boolean isMultiPartFormPost() {
        String contentType = this.getContentType();
        return contentType != null && contentType.contains("multipart/form-data") && HttpMethod.POST.matches(this.getMethod());
    }
    private boolean isFormPost() {
        String contentType = this.getContentType();
        return contentType != null && contentType.contains("application/x-www-form-urlencoded") && org.springframework.http.HttpMethod.POST.matches(this.getMethod());
    }

    @Override
    public String getCharacterEncoding() {
        return getCharset().name();
    }

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
                        name = URLDecoder.decode(name, StandardCharsets.UTF_8);
                    } catch (Exception ignored) {
                    }
                    try {
                        value = URLDecoder.decode(value, StandardCharsets.UTF_8);
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

    private Charset getCharset() {
        try {
            if (charset == null) {
                String enc = getRequest().getCharacterEncoding();
                enc = enc == null ? "UTF-8" : enc;
                charset = B2CConverter.getCharset(enc);
            }
        } catch (UnsupportedEncodingException e) {
            // Ignore
        }
        return charset;
    }
}