package io.github.jevonsnotes.jevokit.core.http.wrapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Field;
import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RequestContentCachingRequestWrapper} 单元测试：字符集解析基于 JDK {@link Charset}，
 * 不依赖容器实现类（原 Tomcat B2CConverter 引用会导致非 Tomcat 容器 NoClassDefFoundError）。
 */
@DisplayName("RequestContentCachingRequestWrapper 字符集解析")
class RequestContentCachingRequestWrapperTest {

    @Test
    @DisplayName("声明正常编码 UTF-8：getCharacterEncoding 原样返回，参数按该编码解析")
    void characterEncodingUtf8() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setCharacterEncoding("UTF-8");
        request.setQueryString("a=1&b=2");
        RequestContentCachingRequestWrapper wrapper = new RequestContentCachingRequestWrapper(request);
        // when / then
        assertThat(wrapper.getCharacterEncoding()).as("声明的 UTF-8 应原样返回").isEqualTo("UTF-8");
        assertThat(wrapper.getParameter("a")).as("query 参数应按解析出的字符集解码").isEqualTo("1");
        assertThat(wrapper.getParameterValues("b")).as("多值参数应返回值数组").containsExactly("2");
    }

    @Test
    @DisplayName("声明语法非法的编码名：解析失败被忽略不中断，charset 保持 null")
    void illegalCharsetNameIgnored() {
        // given："!!!" 含非法字符，Charset.forName 抛 IllegalCharsetNameException
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setCharacterEncoding("!!!not-a-charset");
        RequestContentCachingRequestWrapper wrapper = new RequestContentCachingRequestWrapper(request);
        // when：触发 getCharset 的公开路径不应抛未捕获异常
        assertThat(wrapper.getParameterMap()).as("非法编码名不应中断参数解析").isNotNull();
        // then：charset 保持 null（既有语义）
        assertThat(readCharsetField(wrapper)).as("非法编码名应被忽略，charset 保持 null").isNull();
    }

    @Test
    @DisplayName("声明语法合法但未注册的编码名：解析失败被忽略不中断，charset 保持 null")
    void unsupportedCharsetNameIgnored() {
        // given：语法合法但 JDK 未注册，Charset.forName 抛 UnsupportedCharsetException
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setCharacterEncoding("not-a-real-charset");
        RequestContentCachingRequestWrapper wrapper = new RequestContentCachingRequestWrapper(request);
        // when / then
        assertThat(wrapper.getParameterMap()).as("未知编码名不应中断参数解析").isNotNull();
        assertThat(readCharsetField(wrapper)).as("未知编码名应被忽略，charset 保持 null").isNull();
    }

    private Charset readCharsetField(RequestContentCachingRequestWrapper wrapper) {
        try {
            Field field = RequestContentCachingRequestWrapper.class.getDeclaredField("charset");
            field.setAccessible(true);
            return (Charset) field.get(wrapper);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("读取私有字段 charset 失败", e);
        }
    }
}
