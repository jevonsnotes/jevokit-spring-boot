package io.github.jevonsnotes.jevokit.okhttp.interceptor;

import okhttp3.OkHttpClient;

/**
 * 自定义Okhttp配置
 */
public interface CustomOkhttpConfiguration {

    void configClient(OkHttpClient.Builder builder);

    void configProxyClient(OkHttpClient.Builder builder);
}
