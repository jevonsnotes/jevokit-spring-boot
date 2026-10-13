package io.github.jevonsnotes.jevokit.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OkHttpStatusRuntimeException} 单元测试：抛出型工厂的字段语义
 * （三参形态为兼容历史调用形态，等价于四参形态的响应体置空串）。
 */
@DisplayName("OkHttpStatusRuntimeException 抛出型工厂")
class OkHttpStatusRuntimeExceptionTest {

    @Test
    @DisplayName("四参 ofThrow：code/url/body 字段逐一保留，message 为四元拼接串")
    void ofThrowWithBody() {
        assertThatThrownBy(() -> OkHttpStatusRuntimeException.ofThrow(500, "Internal Error", "http://a/b", "boom"))
                .as("四参工厂应直接抛出该异常")
                .isInstanceOf(OkHttpStatusRuntimeException.class)
                .satisfies(e -> {
                    OkHttpStatusRuntimeException ex = (OkHttpStatusRuntimeException) e;
                    assertThat(ex.getCode()).as("code 应原样保留").isEqualTo(500);
                    assertThat(ex.getUrl()).as("url 应原样保留").isEqualTo("http://a/b");
                    assertThat(ex.getBody()).as("body 应原样保留").isEqualTo("boom");
                    assertThat(ex.getMessage()).as("message 应为四元拼接串").isEqualTo("500 Internal Error http://a/b boom");
                });
    }

    @Test
    @DisplayName("三参 ofThrow（兼容历史调用形态）：code/url 保留，body 置空串，message 为四元拼接串")
    void ofThrowWithoutBody() {
        assertThatThrownBy(() -> OkHttpStatusRuntimeException.ofThrow(404, "Not Found", "http://a/gone"))
                .as("三参工厂应直接抛出该异常")
                .isInstanceOf(OkHttpStatusRuntimeException.class)
                .satisfies(e -> {
                    OkHttpStatusRuntimeException ex = (OkHttpStatusRuntimeException) e;
                    assertThat(ex.getCode()).as("code 应原样保留").isEqualTo(404);
                    assertThat(ex.getUrl()).as("url 应原样保留").isEqualTo("http://a/gone");
                    assertThat(ex.getBody()).as("未传 body 应置空串").isEmpty();
                    assertThat(ex.getMessage()).as("message 应为四元拼接串（body 占位为空）").isEqualTo("404 Not Found http://a/gone ");
                });
    }
}
