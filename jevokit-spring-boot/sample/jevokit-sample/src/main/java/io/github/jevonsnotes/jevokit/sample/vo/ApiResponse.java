package io.github.jevonsnotes.jevokit.sample.vo;

import lombok.Data;

/**
 * 示例统一返回体（code/message/data），sample 各端点的响应包装形态。
 */
@Data
public class ApiResponse<T> {
    private T data;

    public static <T>ApiResponse<T> ok(T data){
        ApiResponse<T> res = new ApiResponse<>();
        res.setData(data);
        return res;
    }
}
