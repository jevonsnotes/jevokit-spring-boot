package springbootkit.test.server;

import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.StrUtil;
import org.junit.Test;
import springbootkit.core.util.JsonUtil;
import springbootkit.core.util.PageUtil;
import springbootkit.core.util.SecureRandomUtil;
import springbootkit.desensitization.consts.DstConst;
import springbootkit.test.server.vo.TestVo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Description TODO
 * @Date 2022/6/10 10:53
 * @Version 0.0.1
 */
public class SimpleTest {
    @Test
    public void test(){
        Object s = new BigDecimal("123");
        if(s instanceof BigDecimal b){
            System.out.println(b);
        }
        var tar = new TestVo();
        TestVo testVo = testVar(tar);
    }

    public TestVo testVar(TestVo a){
        return TestVo.builder().build();
    }

    @Test
    public void testPagination(){
        List<String> testStr = new ArrayList<>();
        for(int i =0;i<998;i++){
            testStr.add(SecureRandomUtil.randomString(8));
        }

        List<String> page = PageUtil.pagination(testStr, 9, 100);
        System.out.println(page);
    }

    @Test
    public void testInnerJson(){
        String json ="{\\\"n12\\\":{\\\"n21\\\":\\\"L2\\\\\\\"\\\",\\\"n22\\\":{\\\"n32\\\":\\\"A4564\\\",\\\"n31\\\":\\\"L3\\\\\\\"\\\"}},\\\"n11\\\":\\\"L1\\\\\\\"\\\"}";
//        String json ="{\\\"ggw\\\":\\\"{\\\\\\\"abc\\\\\\\":\\\\\\\"a\\\\\\\\\\\\\\\"aa\\\\\\\"}\\\"}";
        String json2 ="{\\\"yyy\\\":\\\"{\\\\\\\"ggw\\\\\\\":\\\\\\\"{\\\\\\\\\\\\\\\"abc\\\\\\\\\\\\\\\":\\\\\\\\\\\\\\\"a\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\"aa\\\\\\\\\\\\\\\"}\\\\\\\"}\\\"}";
        int level = 1;
//        while(true){
//            String str = StrUtil.padPre("\"", level * (level - 1) + 2, '\\');
//            if(json.matches(".*\\s*\\{\\s*"+str.replace("\\","\\\\")+".*")){
//                level++;
//            } else if(json.contains(str)){
//                level--;
//                json.replace(str, "abc");
//            } else {
//                level--;
//                break;
//            }
//        }
        while(true){
            String str = StrUtil.padPre("\"", level * (level - 1) + 2, '\\');
            if(json.contains(str)){
                level++;
            } else {
                level--;
                break;
            }
        }
        System.out.println(level);
        for(int i = level ;i>1;i--){
            String str = StrUtil.padPre("\"", i * (i - 1) + 2, '\\');
            json = json.replace(str,"@ph_level_"+i+"@");
        }

        processStrJsonForStringField(json,1,level);
        System.out.println(json);
    }

    private String processStrJson(String json){
        int level = 1;
        while(true){
            String str = StrUtil.padPre("\"", level * (level - 1) + 2, '\\');
            if(json.contains(str)){
                level++;
            } else {
                level--;
                break;
            }
        }
        for(int i = level ;i>1;i--){
            String str = StrUtil.padPre("\"", i * (i - 1) + 2, '\\');
            json = json.replace(str,"@ph_level_"+i+"@");
        }

       return processStrJsonForStringField(json,1,level);
    }

    private String processStrJsonForStringField(String data, int currentDepth, int maxDepth){
        if(StrUtil.isNotBlank(data)){
            StrBuilder sb = new StrBuilder();
            String quoteStr = StrUtil.padPre("\"", currentDepth * (currentDepth - 1) + 2, '\\');
            String format = StrBuilder.create().append(quoteStr).append("%s").append(quoteStr).append("%s:%s").append(quoteStr).append("%s").append(quoteStr).toString();
            String snapshot = data;
            String patternString = Pattern.quote(quoteStr) + "(.*?)" + Pattern.quote(quoteStr) +"(\\s*?)"+ Pattern.quote(":") + "(\\s*?)"  + Pattern.quote(quoteStr) +"(.*?)"  + Pattern.quote(quoteStr);
            Pattern pattern = Pattern.compile(patternString, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(data);
            while (matcher.find()) {
                if(matcher.groupCount() >=4){
                    String group = matcher.group(0);
                    String fieldName = matcher.group(1).substring(matcher.group(1).lastIndexOf(quoteStr)+1);
                    String value = matcher.group(4);//unescapeJson(matcher.group(4));
                    String enc;
                    if(JsonUtil.isTypeJSON(value)){
                        enc = processStrJsonForStringField(unescapeInnerJson(value,currentDepth,maxDepth,true),currentDepth+1,maxDepth);
                    } else {
                        value = unescapeInnerJson(value,currentDepth,maxDepth,false);
                        enc = "密文";//DesUtil.desByProperties(fieldName,value);
                    }
                    snapshot = collectDataAndReturnRest(snapshot,group,String.format(format,matcher.group(1),matcher.group(2),matcher.group(3),enc),sb);
                }
            }
            data = sb.append(snapshot).toString();
        }
        return data;
    }
    private String unescapeInnerJson(String json,int currentDepth,int maxDepth,boolean onlyNextDepth){
        if(onlyNextDepth){
            int nextDepth = currentDepth+1;
            if(nextDepth<=maxDepth){
                String source = StrUtil.padPre("\"", nextDepth * (nextDepth - 1) + 2, '\\');
                json = json.replace("@ph_level_"+nextDepth+"@",source);
            }
        } else {

            for(int i=maxDepth;i>currentDepth;i--){
                String source = StrUtil.padPre("\"", i * (i - 1) + 2, '\\');
                json = json.replace("@ph_level_"+i+"@",source);
            }
        }
        return json;
    }
    /**
     * 数据拼接
     * @param snapshot 数据快照
     * @param subStr 字串
     * @param subStrAfterProcess 处理后的字串
     * @param collector 拼接器
     * @return 返回剩余数据快照
     */
    private static String collectDataAndReturnRest(String snapshot, String subStr, String subStrAfterProcess, StrBuilder collector){
        int subStrIdx = snapshot.indexOf(subStr);
        collector.append(snapshot.substring(0,subStrIdx));
        if(isSimilarEnc(subStrAfterProcess) || isSimilarPlaceHolder(subStr)){
            collector.append(subStrAfterProcess);
        } else {
            collector.append(subStr);
        }
        return snapshot.substring(subStrIdx+subStr.length());
    }

    /**
     * 是否脱敏数据
     * @param val 内容
     * @return 返回布尔值
     */
    private static boolean isSimilarEnc(String val){
        return StrUtil.isNotBlank(val) && (val.contains(DstConst.DESENSITIZED_ENC_KEY)|| val.contains("*"));
    }
    /**
     * 是否脱敏数据
     * @param val 内容
     * @return 返回布尔值
     */
    private static boolean isSimilarPlaceHolder(String val){
        return StrUtil.isNotBlank(val) && (val.contains("@ph_level_"));
    }
}
