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
/**
    * POST 演示：审计事件规则 CREATE 的验证靶点。
    * @param vo 任意业务体
    * @return 统一响应
    */
    public ApiResponse<Object> create(@RequestBody Object vo) {
        return ApiResponse.ok(vo);
    }

    @PutMapping({"/"})
/**
    * PUT 演示：审计事件规则 UPDATE 的验证靶点。
    * @param vo 任意业务体
    * @return 统一响应
    */
    public ApiResponse<Object> update(@RequestBody Object vo) {
        return ApiResponse.ok(vo);
    }

    @GetMapping({"/{id}"})
/**
    * GET 演示：审计事件规则 QUERY（路径变量形态）的验证靶点。
    * @param id 资源 id
    * @return 统一响应
    */
    public ApiResponse<Object> select(@PathVariable("id") Object id) {
        return ApiResponse.ok(id);
    }

    @DeleteMapping({"/{ids}"})
/**
    * DELETE 演示：审计事件规则 DELETE（批量路径变量）的验证靶点。
    * @param ids 资源 id 集合
    * @return 统一响应
    */
    public ApiResponse<Integer> delete(@PathVariable("ids") List<Object> ids) {
        return ApiResponse.ok(ids.size());
    }

    @PostMapping({"/search"})
/**
    * POST 查询演示：审计事件规则 QUERY（/search 后缀形态）的验证靶点。
    * @param vo 查询体
    * @return 统一响应
    */
    public ApiResponse<Object> search(@RequestBody Object vo) {
        return ApiResponse.ok(vo);
    }
}
