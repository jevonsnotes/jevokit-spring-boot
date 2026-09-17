package io.github.jevonsnotes.jevokit.nogen.api;

import cn.hutool.core.collection.ConcurrentHashSet;
import cn.hutool.core.thread.ExecutorBuilder;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HtmlUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.poi.word.TableUtil;
import com.alibaba.fastjson.JSONObject;
import io.netty.util.concurrent.CompleteFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.github.jevonsnotes.jevokit.configuration.annotation.XssClean;
import io.github.jevonsnotes.jevokit.core.consts.SimpleConsts;
import io.github.jevonsnotes.jevokit.core.util.LoopUtil;
import io.github.jevonsnotes.jevokit.nogen.helper.BzNoHelper;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.DateColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.Source;
import tech.tablesaw.io.json.JsonReadOptions;
import tech.tablesaw.io.json.JsonReader;
import tech.tablesaw.io.json.JsonWriteOptions;

import java.io.ByteArrayInputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 单号生成API
 */
@Slf4j
@RestController
@RequestMapping("/jevokit/nogen")
@RequiredArgsConstructor
public class JevokitNoGenApi {
    /**
     * 单号获取线程池
     */
    private static final ExecutorService executor = ExecutorBuilder.create()
            .setCorePoolSize(BzNoHelper.CORES)
            .setMaxPoolSize(BzNoHelper.CORES)
            .setWorkQueue(new LinkedBlockingQueue<>(Integer.MAX_VALUE))
            .setHandler(new ThreadPoolExecutor.CallerRunsPolicy())
            .build();

    private final BzNoHelper bzNoHelper;

    @RequestMapping("/getNo")
    public String getNo(@RequestParam("bizTag") String bizTag) {
        return bzNoHelper.getNo(bizTag);
    }

    @RequestMapping("/view/tags")
    public String viewTags() {
        JsonReadOptions opt = JsonReadOptions.builderFromString(JSONObject.toJSONString(bzNoHelper.getTagsVo()))
                .build();
        return new JsonReader().read(opt).setName("单号服务配置信息").printAll();
    }

    /**
     * 左填充单号接口
     *
     * @param bizTag
     * @param length
     * @param pad
     * @return
     */
    @RequestMapping("/getNoLPad")
    public String getNo(@RequestParam("bizTag") String bizTag, @RequestParam("length") int length, @RequestParam("pad") String pad) {
        return bzNoHelper.getNoLPad(bizTag, length, pad);
    }

    /**
     * 右填充单号接口
     *
     * @param bizTag
     * @param length
     * @param pad
     * @return
     */
    @RequestMapping("/getNoRPad")
    public String getNoRPad(@RequestParam("bizTag") String bizTag, @RequestParam("length") int length, @RequestParam("pad") String pad) {
        return bzNoHelper.getNoRPad(bizTag, length, pad);
    }

    /**
     * BzNoMode.RECYCLE
     * 回收单号接口，供回收模式下使用，已回收的单号会进入单号池，可以再次通过getNo获取
     *
     * @param bizTag
     * @param bizNo
     * @return
     */
    @RequestMapping("/recycle")
    public String recycle(@RequestParam("bizTag") String bizTag, @RequestParam("bizNo") String bizNo) {
        return bzNoHelper.recycle(bizTag, bizNo) ? SimpleConsts.SUCCESS : SimpleConsts.FAIL;
    }

    /**
     * 批量获取单号
     *
     * @param bizTag
     * @param size
     * @return
     */
    @RequestMapping("/getNos")
    public Set<String> getNos(@RequestParam("bizTag") String bizTag, @RequestParam("size") int size) {
        Set<String> nos = new HashSet<>();
        if (size > 100) {

            Supplier<String> task = ()-> bzNoHelper.getNo(bizTag);

            List<CompletableFuture<String>> futures = LoopUtil.supplyToList(100, () -> CompletableFuture.supplyAsync(task, executor));

            nos = futures.stream().map(CompletableFuture::join).filter(StrUtil::isNotBlank).collect(Collectors.toSet());

        } else {
            while (size-- > 0) {
                nos.add(String.valueOf(bzNoHelper.getNo(bizTag)));
            }

        }
        return nos;
    }

    @RequestMapping("/cache/clear")
    public String cacheClear() {
        bzNoHelper.clearHook();
        return SimpleConsts.SUCCESS;
    }
}
