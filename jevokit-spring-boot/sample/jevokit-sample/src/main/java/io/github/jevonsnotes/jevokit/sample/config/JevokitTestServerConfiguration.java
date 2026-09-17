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
 * @Description TODO
 * @Date 2024/9/2
 * @Version 0.0.1
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
