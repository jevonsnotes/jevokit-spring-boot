package springbootkit.test.server.api;

import org.springframework.web.bind.annotation.*;
import springbootkit.core.lang.ApiResponse;

import java.util.List;

/**
 * @Description TODO
 * @Date 2022/6/29 18:10
 * @Version 0.0.1
 */
@RestController
@RequestMapping("/restful")
public class RestfulApi {
    @PostMapping({"/"})
    public ApiResponse<Object> create(@RequestBody Object vo) {
        return ApiResponse.ok(vo);
    }

    @PutMapping({"/"})
    public ApiResponse<Object> update(@RequestBody Object vo) {
        return ApiResponse.ok(vo);
    }

    @GetMapping({"/{id}"})
    public ApiResponse<Object> select(@PathVariable("id") Object id) {
        return ApiResponse.ok(id);
    }

    @DeleteMapping({"/{ids}"})
    public ApiResponse<Integer> delete(@PathVariable("ids") List<Object> ids) {
        return ApiResponse.ok(ids.size());
    }

    @PostMapping({"/search"})
    public ApiResponse<Object> search(@RequestBody Object vo) {
        return ApiResponse.ok(vo);
    }
}
