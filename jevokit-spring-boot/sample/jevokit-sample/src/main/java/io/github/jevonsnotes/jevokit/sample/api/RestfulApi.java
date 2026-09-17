package io.github.jevonsnotes.jevokit.sample.api;

import org.springframework.web.bind.annotation.*;
import io.github.jevonsnotes.jevokit.core.lang.ApiResponse;

import java.util.List;

/**
 * RESTful CRUD 演示端点（/restful/*）：标准增删改查形态，审计日志事件规则
 * （GET=QUERY/POST=CREATE/PUT=UPDATE/DELETE=DELETE）的验证靶点。
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
