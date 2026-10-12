package io.github.jevonsnotes.jevokit.okhttp.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.jevonsnotes.jevokit.okhttp.interceptor.CustomOkhttpConfiguration;
import io.github.jevonsnotes.jevokit.okhttp.util.OkHttpUtil;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 代理连接失败日志完整性回归：触发真实代理失败（指向本机必拒端口），
 * 断言三要素——失败原因（异常类名+message）、代理与业务目标地址、完整堆栈（throwableProxy）。
 * 用例末尾把捕获的日志原文打到 stdout，便于人工查看最终日志形态。
 */
class JevokitOkhttpProxyFailLogTest {

    @Test
    @DisplayName("代理连接失败：日志含失败原因/代理/目标地址并带完整堆栈")
    void proxyConnectFailureLogsContainCauseTargetAndStack() throws Exception {
        OkhttpProperties props = new OkhttpProperties();
        props.setProxyMode(OkhttpProperties.ProxyMode.always);
        props.setProxyCheckLevel(OkhttpProperties.CheckLevel.all);
        props.setProxyHosts(Arrays.asList("127.0.0.1:1")); // 本机必拒端口，触发 Connection refused
        props.setConnectTimeout(3);
        props.setReadTimeout(3);

        JevokitOkhttpConfiguration configuration = new JevokitOkhttpConfiguration();
        Field propertiesField = JevokitOkhttpConfiguration.class.getDeclaredField("properties");
        propertiesField.setAccessible(true);
        propertiesField.set(configuration, props);

        Logger logger = (Logger) LoggerFactory.getLogger(JevokitOkhttpConfiguration.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            CustomOkhttpConfiguration noOpCustom = new CustomOkhttpConfiguration() {
                @Override
                public void configClient(okhttp3.OkHttpClient.Builder builder) {
                }

                @Override
                public void configProxyClient(okhttp3.OkHttpClient.Builder builder) {
                }
            };
            OkHttpClient proxyClient = configuration.okHttpProxyClient(noOpCustom, configuration.pool(props));
            OkHttpUtil util = new OkHttpUtil(props, null, proxyClient);
            try {
                util.get("http://example.invalid/proxy-fail-probe");
            } catch (Exception expected) {
                // 代理不可用，请求失败是预期
            }

            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getFormattedMessage()).contains("ConnectException");
                assertThat(event.getFormattedMessage()).contains("127.0.0.1:1");
                assertThat(event.getFormattedMessage()).contains("target [http://example.invalid/proxy-fail-probe]");
                assertThat(event.getThrowableProxy()).isNotNull();
            });
        } finally {
            logger.detachAppender(appender);
        }

        System.out.println("======== 代理连接失败实际日志输出（START） ========");
        appender.list.forEach(event -> {
            System.out.println("[LOG] " + event.getFormattedMessage());
            if (event.getThrowableProxy() != null) {
                System.out.println("[LOG-STACK] " + event.getThrowableProxy().getClassName() + ": " + event.getThrowableProxy().getMessage());
            }
        });
        System.out.println("======== 代理连接失败实际日志输出（END） ========");
    }
}
