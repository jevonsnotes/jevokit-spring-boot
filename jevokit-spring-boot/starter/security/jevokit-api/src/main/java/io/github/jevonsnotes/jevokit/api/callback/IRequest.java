package io.github.jevonsnotes.jevokit.api.callback;

import java.util.Map;

/**
 * @Description TODO
 * @Date 2025/4/14
 * @Version 0.0.1
 */
public interface IRequest {
    String call(String url, String body, Map<String, String> headers);
}
