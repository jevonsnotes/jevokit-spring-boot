package io.github.jevonsnotes.jevokit.core.util;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import io.github.jevonsnotes.jevokit.core.holder.IntHolder;

import io.github.jevonsnotes.jevokit.core.lang.CallBackP2R;

/**
 * JSON 处理工具，在 hutool JSONUtil 基础上补充 fastjson2 结构的递归字符串编辑能力。
 * <p>常用入口：{@link #editJsonObj(JSONObject, CallBackP2R)}；数组侧 {@link #editJsonArr(String, JSONArray, CallBackP2R)}
 * 通常由前者递归调用，也可单独使用。
 * <p>关键语义：深度优先遍历整棵 JSON 树，仅字符串叶子值交给回调并原地替换，
 * 数字/布尔/null 等其他标量原样保留；嵌套容器继续下钻，容器本身不参与回调。
 */
@Slf4j
public class JsonUtil extends JSONUtil {
    /**
     * 递归编辑 JSON 对象中的字符串值（原地修改）
     *
     * @param jsonObject 待编辑的 fastjson2 对象
     * @param callback   字符串转换回调，入参为 (所在属性名, 原字符串值)，返回替换值
     */
    public static void editJsonObj(JSONObject jsonObject, CallBackP2R<Object,String,Object> callback) {
        jsonObject.forEach((k,v)->{
            if(v instanceof JSONObject){
                editJsonObj((JSONObject)v,callback);
            } else if(v instanceof JSONArray){
                editJsonArr(k,(JSONArray) v,callback);
            } else if(v instanceof String){
                jsonObject.put(k,callback.call(k,v));
            }
        });
    }
    /**
     * 递归编辑 JSON 数组中的字符串元素（原地修改）
     *
     * @param key       数组所属的属性名，嵌套数组沿用最外层属性名透传给回调
     * @param jsonArray 待编辑的 fastjson2 数组
     * @param callback  字符串转换回调，入参为 (属性名, 原字符串值)，返回替换值
     */
    public static void editJsonArr(String key, JSONArray jsonArray, CallBackP2R<Object,String,Object> callback) {
        IntHolder idx = new IntHolder(0);
        jsonArray.forEach(ele ->{
            if(ele instanceof JSONObject){
                editJsonObj((JSONObject)ele,callback);
            } else if(ele instanceof JSONArray){
                editJsonArr(key,(JSONArray) ele,callback);
            } else if(ele instanceof String){
                jsonArray.set(idx.value, callback.call(key,ele));
            }
            idx.value++;
        });
    }
}
