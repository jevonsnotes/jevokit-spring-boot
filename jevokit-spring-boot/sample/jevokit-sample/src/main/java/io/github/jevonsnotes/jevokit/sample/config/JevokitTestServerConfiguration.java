package io.github.jevonsnotes.jevokit.sample.config;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.jevonsnotes.jevokit.okhttp.interceptor.CustomOkhttpConfiguration;

import java.io.IOException;

/**
 * 测试服务个性化配置：向 okhttp 注册 CustomOkhttpConfiguration（含模拟上游
 * MockWebServer 拦截器），E2E 网络类用例的桩服务基座。
 */
@Slf4j
@Configuration
public class JevokitTestServerConfiguration {
    @Bean
    public CustomOkhttpConfiguration okhttpInterceptor() {
        return new CustomOkhttpConfiguration(){
            @Override
            public void configClient(OkHttpClient.Builder builder) {
                addInterceptors(builder);
            }

            @Override
            public void configProxyClient(OkHttpClient.Builder builder) {
                addInterceptors(builder);
            }
        };

    }

    private void addInterceptors(OkHttpClient.Builder builder){
        builder.addInterceptor(new Interceptor() {
            @NotNull
            @Override
            public Response intercept(@NotNull Chain chain) throws IOException {
                log.info("i am a normal okhttp interceptor");
                return chain.proceed(chain.request());
            }
        });
        builder.addNetworkInterceptor(new Interceptor() {
            @NotNull
            @Override
            public Response intercept(@NotNull Chain chain) throws IOException {
                log.info("i am a network okhttp interceptor");
                return chain.proceed(chain.request());
            }
        });
    }
}
