package io.github.jevonsnotes.jevokit.core.util;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JsonUtil} 单元测试：JSONObject/JSONArray 内字符串值的递归编辑。
 */
@DisplayName("JsonUtil 递归编辑")
class JsonUtilTest {

    /** 统一的编辑回调：CallBackP2R&lt;R,P1,P2&gt; 中 P2 为 Object，回调内按 String 处理 */
    private static Object clean(Object key, Object value) {
        return String.valueOf(value).replace("-raw", "-clean");
    }

    @Test
    @DisplayName("editJsonObj：嵌套对象/数组中的字符串被编辑，非字符串值不动")
    void editJsonObjRecursively() {
        // given：含嵌套对象、嵌套数组、数字与布尔值的 JSON
        JSONObject root = new JSONObject();
        root.put("name", "tom-raw");
        root.put("age", 18);
        root.put("active", true);
        JSONObject address = new JSONObject();
        address.put("city", "bj-raw");
        address.put("zip", 100000);
        root.put("address", address);
        JSONArray phones = new JSONArray();
        phones.add("123-raw");
        phones.add(456);
        root.put("phones", phones);

        // when：把所有 "-raw" 后缀替换为 "-clean"
        JsonUtil.editJsonObj(root, JsonUtilTest::clean);

        // then
        assertThat(root.getString("name")).as("顶层字符串应被编辑").isEqualTo("tom-clean");
        assertThat(root.getIntValue("age")).as("数字值应原样保留").isEqualTo(18);
        assertThat(root.getBoolean("active")).as("布尔值应原样保留").isTrue();
        assertThat(root.getJSONObject("address").getString("city"))
                .as("嵌套 JSONObject 中的字符串应递归编辑").isEqualTo("bj-clean");
        assertThat(root.getJSONObject("address").getIntValue("zip"))
                .as("嵌套对象中的数字应原样保留").isEqualTo(100000);
        assertThat(root.getJSONArray("phones").getString(0))
                .as("嵌套 JSONArray 中的字符串应递归编辑").isEqualTo("123-clean");
        assertThat(root.getJSONArray("phones").getIntValue(1))
                .as("嵌套数组中的数字应原样保留").isEqualTo(456);
    }

    @Test
    @DisplayName("editJsonObj：嵌套数组中的对象也被递归编辑")
    void editJsonObjNestedArrayElement() {
        // given
        JSONObject root = new JSONObject();
        JSONArray users = new JSONArray();
        JSONObject user = new JSONObject();
        user.put("nick", "a-raw");
        users.add(user);
        root.put("users", users);
        // when
        JsonUtil.editJsonObj(root, JsonUtilTest::clean);
        // then：数组内的对象元素同样被处理（editJsonArr → editJsonObj 链路）
        assertThat(root.getJSONArray("users").getJSONObject(0).getString("nick"))
                .as("数组内对象元素的字符串应被递归编辑").isEqualTo("a-clean");
    }

    @Test
    @DisplayName("editJsonArr：数组元素按索引原位替换字符串")
    void editJsonArrInPlace() {
        // given
        JSONArray arr = new JSONArray();
        arr.add("x-raw");
        arr.add(1);
        arr.add("y-raw");
        // when
        JsonUtil.editJsonArr("key", arr, JsonUtilTest::clean);
        // then
        assertThat(arr.getString(0)).as("第 0 个字符串元素应被替换").isEqualTo("x-clean");
        assertThat(arr.getIntValue(1)).as("非字符串元素位置不变").isEqualTo(1);
        assertThat(arr.getString(2)).as("第 2 个字符串元素应被替换").isEqualTo("y-clean");
    }

    @Test
    @DisplayName("editJsonArr：嵌套数组（数组套数组）递归编辑")
    void editJsonArrNestedArray() {
        // given
        JSONArray outer = new JSONArray();
        JSONArray inner = new JSONArray();
        inner.add("deep-raw");
        outer.add(inner);
        // when
        JsonUtil.editJsonArr("k", outer, JsonUtilTest::clean);
        // then
        assertThat(outer.getJSONArray(0).getString(0))
                .as("嵌套数组的字符串元素应被递归编辑").isEqualTo("deep-clean");
    }

    @Test
    @DisplayName("回调收到当前键名：可按键名做差异化编辑")
    void callbackReceivesKey() {
        // given
        JSONObject root = new JSONObject();
        root.put("password", "secret");
        root.put("note", "plain");
        // when：仅对 password 键脱敏（P1 为键名，P2 为值）
        JsonUtil.editJsonObj(root, (k, v) -> "password".equals(k) ? "***" : v);
        // then
        assertThat(root.getString("password")).as("password 键应被脱敏").isEqualTo("***");
        assertThat(root.getString("note")).as("其他键不受影响").isEqualTo("plain");
    }
}
