package io.github.jevonsnotes.jevokit.sample.api;

import cn.hutool.core.lang.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.github.jevonsnotes.jevokit.core.annotation.Excel;
import io.github.jevonsnotes.jevokit.core.lang.ApiResponse;
import io.github.jevonsnotes.jevokit.sample.vo.ExcelVo;

import jakarta.validation.Valid;
import java.util.List;

/**
 * @Description TODO
 * @Date 2022/7/6 9:55
 * @Version 0.0.1
 */
@RestController
@RequestMapping("excel")
public class ExcelApi {


    @RequestMapping("importExcel")
    public ApiResponse importExcel(
            @Excel(ignoreRows = 1) List<ExcelVo> file1,
            @Excel(ignoreRows = 1,sheet = 1) List<ExcelVo> file2){
        Assert.isTrue(!file1.isEmpty());
        return ApiResponse.ok("success");
    }

}
