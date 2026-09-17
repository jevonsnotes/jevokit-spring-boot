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
 * excel 导入演示端点（/excel/importExcel）：@Excel List&lt;ExcelVo&gt; 入参直接绑定
 * multipart 上传（支持多文件、按 sheet/ignoreRows 区分），无需手写解析。
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
