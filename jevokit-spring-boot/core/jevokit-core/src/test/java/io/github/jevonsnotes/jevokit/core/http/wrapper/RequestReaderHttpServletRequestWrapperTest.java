package io.github.jevonsnotes.jevokit.core.http.wrapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RequestReaderHttpServletRequestWrapper} 单元测试：字符集解析基于 JDK {@link Charset}，
 * 不依赖容器实现类（原 Tomcat B2CConverter / coyote Constants 引用会导致非 Tomcat 容器 NoClassDefFoundError）。
 * 类虽 @Deprecated 但仍在发布产物中，行为须保持可用。
 */
@DisplayName("RequestReaderHttpServletRequestWrapper 字符集解析")
class RequestReaderHttpServletRequestWrapperTest {

    @Test
    @DisplayName("声明正常编码 UTF-8：参数按该编码解析")
    void characterEncodingUtf8() throws IOException {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setCharacterEncoding("UTF-8");
        request.setQueryString("a=1&b=2");
        RequestReaderHttpServletRequestWrapper wrapper = new RequestReaderHttpServletRequestWrapper(request);
        // when / then
        assertThat(wrapper.getParameter("a")).as("query 参数应按声明的 UTF-8 解析").isEqualTo("1");
        assertThat(wrapper.getParameterValues("b")).as("多值参数应返回值数组").containsExactly("2");
    }

    @Test
    @DisplayName("声明非法编码名：解析被忽略不中断，按 UTF-8 兜底（Tomcat 10.1 语义延续）")
    void illegalCharsetNameFallsBackToUtf8() throws IOException {
        // given："!!!" 含非法字符，Charset.forName 抛 IllegalCharsetNameException
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setCharacterEncoding("!!!not-a-charset");
        request.setQueryString("name=" + URLEncoder.encode("张三", StandardCharsets.UTF_8));
        RequestReaderHttpServletRequestWrapper wrapper = new RequestReaderHttpServletRequestWrapper(request);
        // when：公开路径不应抛未捕获异常
        // then：声明编码被忽略后按 UTF-8 兜底解码，值仍可正确还原
        assertThat(wrapper.getParameter("name")).as("非法编码名应被忽略并按 UTF-8 兜底解码").isEqualTo("张三");
        assertThat(readCharsetField(wrapper)).as("解析失败时缓存字段应保持 null（兜底由 getCharset 返回）").isNull();
    }

    private Charset readCharsetField(RequestReaderHttpServletRequestWrapper wrapper) {
        try {
            Field field = RequestReaderHttpServletRequestWrapper.class.getDeclaredField("charset");
            field.setAccessible(true);
            return (Charset) field.get(wrapper);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("读取私有字段 charset 失败", e);
        }
    }
}
