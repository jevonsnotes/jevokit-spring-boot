package springbootkit.sample.vo;

import lombok.Data;

/**
 * @Description TODO
 * @Date 2022/5/23 18:31
 * @Version 0.0.1
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
