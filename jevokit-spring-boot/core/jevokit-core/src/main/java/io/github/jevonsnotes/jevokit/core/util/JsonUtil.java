package io.github.jevonsnotes.jevokit.core.util;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import io.github.jevonsnotes.jevokit.core.holder.IntHolder;

import io.github.jevonsnotes.jevokit.core.lang.CallBackP2R;

/**
 * @Description TODO
 * @Date 2024/11/21
 * @Version 0.0.1
 */
@Slf4j
public class JsonUtil extends JSONUtil {
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
