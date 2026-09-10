package springbootkit.okhttp.interceptor;

import okhttp3.OkHttpClient;

/**
 * @Description 自定义Okhttp配置
 * @Date 2024/9/2
 * @Version 0.0.1
 */
public interface CustomOkhttpConfiguration {

    void configClient(OkHttpClient.Builder builder);

    void configProxyClient(OkHttpClient.Builder builder);
}
