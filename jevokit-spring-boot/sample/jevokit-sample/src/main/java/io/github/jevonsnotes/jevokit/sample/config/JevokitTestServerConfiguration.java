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
    /**
    * okhttp 个性化定制 Bean：向直连/代理客户端注册 MockWebServer 桩拦截器（E2E 网络用例的
    * 可控上游）。
    * @return 定制配置
    */
    public CustomOkhttpConfiguration okhttpInterceptor() {
        return new CustomOkhttpConfiguration(){
            /**
            * 覆写：向直连客户端注册桩拦截器。
            */
            @Override
            public void configClient(OkHttpClient.Builder builder) {
                addInterceptors(builder);
            }

            /**
            * 覆写：向代理客户端注册桩拦截器。
            */
            @Override
            public void configProxyClient(OkHttpClient.Builder builder) {
                addInterceptors(builder);
            }
        };

    }

    /**
    * 注册 MockWebServer 拦截器到 Builder。
    */
    private void addInterceptors(OkHttpClient.Builder builder){
        builder.addInterceptor(new Interceptor() {
            @NotNull
            /**
            * 拦截器覆写：同上（代理客户端侧）。
            */
            @Override
            public Response intercept(@NotNull Chain chain) throws IOException {
                log.info("i am a normal okhttp interceptor");
                return chain.proceed(chain.request());
            }
        });
        builder.addNetworkInterceptor(new Interceptor() {
            @NotNull
            /**
            * 拦截器覆写：转发到内嵌 MockWebServer（第二个桩，代理客户端侧）。
            */
            @Override
            public Response intercept(@NotNull Chain chain) throws IOException {
                log.info("i am a network okhttp interceptor");
                return chain.proceed(chain.request());
            }
        });
    }
}
