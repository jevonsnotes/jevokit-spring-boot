package io.github.jevonsnotes.jevokit.desensitization.util;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import io.github.jevonsnotes.jevokit.core.holder.StringHolder;
import io.github.jevonsnotes.jevokit.core.lang.Callback;
import io.github.jevonsnotes.jevokit.core.lang.CallbackR;
import io.github.jevonsnotes.jevokit.core.util.JsonUtil;
import io.github.jevonsnotes.jevokit.core.util.SecurityUtil;
import io.github.jevonsnotes.jevokit.core.util.XmlUtil;
import io.github.jevonsnotes.jevokit.desensitization.config.JevokitDesensitizationProperties;
import io.github.jevonsnotes.jevokit.desensitization.consts.*;
import io.github.jevonsnotes.jevokit.desensitization.desensitizer.Desensitizer;
import io.github.jevonsnotes.jevokit.desensitization.dto.DstContext;
import io.github.jevonsnotes.jevokit.desensitization.helper.DesHelper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Description TODO
 * @Date 2022/8/3 10:01
 * @Version 0.0.1
 */
@Slf4j
public class DesUtil {

    private static final Map<Class<? extends Desensitizer>, Desensitizer> CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<DstProcessType> PROCESS_TYPE = new ThreadLocal<>();
    private static final String XML_C_DATA_START = "<![CDATA[";
    private static final String XML_C_DATA_END = "]]>";

    private static final String JSON_ARRAY_START  = "[";
    private static final String JSON_ARRAY_END = "]";
    private static final String STR_JSON_PLACEHOLDER_PREFIX = "@STR_JSON_L";
    private static final String STR_JSON_PLACEHOLDER_SUFFIX = "@";
    private static final String STR_JSON_PLACEHOLDER = STR_JSON_PLACEHOLDER_PREFIX + "%S" +STR_JSON_PLACEHOLDER_SUFFIX;
    public static Map<String,String> JSON_PLACEHOLDER = new HashMap<>();
    static {
        JSON_PLACEHOLDER.put("%dst_ph@quote%","\\\"");
    }


    public static Desensitizer getDesensitizer(Class<? extends Desensitizer> clazz) {
        return CACHE.computeIfAbsent(clazz, c -> {
            try {
                return c.newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    /**
     * 根据JevokitDesensitizationProperties配置来进行脱敏
     * @param propName 属性名
     * @param value 属性值
     * @return 脱敏数据
     */
    public static String desByProperties(String propName, String value) {

        DstContext dstContext = propPatternMatch(propName);
        if (dstContext != null) {
            value = des(value, dstContext);
        }
        return value;
    }

    /**
     * 匹配并生成脱敏上下文
     * @param fieldName 属性名
     * @return 脱敏上下文
     */
    private static DstContext propPatternMatch(String fieldName) {
        if (fieldName != null) {

            JevokitDesensitizationProperties properties = getProperties();
            if(!isPropIgnore(fieldName)){
                Map<DstLevel, Map<DstType, List<String>>> propPattern = MapUtil.sort(properties.getPropPattern(),Comparator.naturalOrder());

                for (Map.Entry<DstLevel, Map<DstType, List<String>>> levelEntry : propPattern.entrySet()) {
                    DstLevel level = levelEntry.getKey();
                    Map<DstType, List<String>> value = levelEntry.getValue();
                    for(Map.Entry<DstType, List<String>> entry: value.entrySet()) {
                        DstType dstType = entry.getKey();
                        List<String> patterns = entry.getValue();
                        for (String pattern : patterns) {
                            try {
                                String tempFieldName = pattern.contains("\\.") ? fieldName : fieldName.substring(fieldName.lastIndexOf(".")+1);
                                if (tempFieldName.matches(pattern)) {
                                    return DstContext.builder().type(dstType).level(level).build();
                                }
                            } catch (Exception e) {
                                //不阻断
                                log.warn("脱敏模式匹配失败，请检查配置：{}", pattern);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }


    /**
     * 是否忽略脱敏
     * @param fieldName 属性名
     * @return 返回布尔值
     */
    private static boolean isPropIgnore(String fieldName){
        JevokitDesensitizationProperties properties = getProperties();
        if(properties != null){
            for(String ignore : properties.getIgnorePattern()){
                String tempFieldName = ignore.contains("\\.") ? fieldName : fieldName.substring(fieldName.lastIndexOf(".")+1);
                if(tempFieldName.matches(ignore)){
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    /**
     * 脱敏规则
     *
     * @param value
     * @return
     */
    public static String des(String value, DstContext dstContext) {
        if(dstContext != null && !StrUtil.trimToEmpty(value).startsWith(DstConst.DESENSITIZED_ENC_KEY)){
            JevokitDesensitizationProperties properties = getProperties();
            DstPowerCheckPeriod checkPeriod = properties == null ? DstPowerCheckPeriod.DECRYPT : properties.getPowerCheckPeriod();
            if((checkPeriod == DstPowerCheckPeriod.ENCRYPT && !DesHelper.checkPower(dstContext.getLevel()))
                    || checkPeriod == DstPowerCheckPeriod.DECRYPT
                    || isProcessLog()){
                DstAlgorithm alg = currentAlgorithm();
                if (dstContext.getType() != null) {
                    if (DstAlgorithm.BASIC.equals(alg)) {
                        value = processBasic(value, dstContext);
                    } else if (DstAlgorithm.SM4.equals(alg)) {
                        value = processSM4(value, dstContext);
                    } else if (DstAlgorithm.MIXED.equals(alg)) {
                        value = processMixed(value, dstContext);
                    }
                }
            }
        }
        return value;
    }

    /**
     * 获取当前脱敏算法
     * @return 返回算法
     */
    public static DstAlgorithm currentAlgorithm(){
        JevokitDesensitizationProperties properties = getProperties();
        if (properties != null){
            if(isProcessLog()){
                return properties.getAlgorithm().getLog();
            } else {
                return properties.getAlgorithm().getView();
            }

        }
        return DstAlgorithm.BASIC;

    }


    /**
     * 处理混合脱敏
     * @param value 明文
     * @param dstContext 脱敏上下文
     * @return 脱敏数据
     */
    public static String processMixed(String value, DstContext dstContext) {
        return processMixed(value,null,dstContext);
    }

    /**
     * 脱敏 SM4 密钥（显式 key 化：库级内置密钥已移除，见 spkit.crypto.sm4-key）
     * @return SM4 密钥
     */
    private static String sm4Key() {
        String key = SpringUtil.getProperty("spkit.crypto.sm4-key");
        if (StrUtil.isBlank(key)) {
            throw new IllegalStateException("crypto keys not configured: spkit.crypto.sm4-key");
        }
        return key;
    }

    /**
     * 处理混合脱敏
     * @param value 明文
     * @param basicValue 基础脱敏数据
     * @param dstContext 脱敏上下文
     * @return 脱敏数据
     */
    public static String processMixed(String value,String basicValue, DstContext dstContext) {

        return String.format(DstConst.DESENSITIZED_ENC_MIXED_TEMPLATE,
                SecurityUtil.sm4cbcEncrypt( String.format(DstConst.DESENSITIZED_ENC_ASE_SUB_TEMPLATE,
                                Optional.ofNullable(dstContext.getLevel()).orElse(DstLevel.LV9).name(),
                                value), sm4Key()),
                (StrUtil.isBlank(basicValue) ? processBasic(value, dstContext) : maskSpecChar(basicValue)).replace(")","*").replace("[","*").replace("]","*")
        );
    }

    /**
     * 特殊字符掩码（替换容易引起格式异常的字符）
     * @param val 内容
     * @return 返回掩码后的内容
     */
    private static String maskSpecChar(String val){
        if(StrUtil.isNotBlank(val)){
            String regex = val.contains(DstConst.DESENSITIZED_ENC_KEY) ? "[()\\[\\]\\\\<>\"]" :"[\\[\\]\\\\<>\"]";
            val = val.replaceAll(regex,"*");
        }
        return val;
    }

    /**
     * 国密SM4-CBC加密
     * @param value 明文
     * @param dstContext 脱敏上下文
     * @return 脱敏数据
     */
    public static String processSM4(String value, DstContext dstContext) {
        return String.format(DstConst.DESENSITIZED_ENC_ASE_TEMPLATE,
                SecurityUtil.sm4cbcEncrypt(
                        String.format(DstConst.DESENSITIZED_ENC_ASE_SUB_TEMPLATE,
                                Optional.ofNullable(dstContext.getLevel()).orElse(DstLevel.LV9).name(),
                                value), sm4Key())
        );
    }

    /**
     * 脱敏（基础类型）
     * @param value 明文数据
     * @param dstContext 脱敏对象上下文
     * @return 脱敏数据；value 为 null、或 dstContext/type 为 null 时原样返回（不脱敏不抛错）
     */
    public static String processBasic(String value, DstContext dstContext) {
        if (value == null || dstContext == null || dstContext.getType() == null) {
            return value;
        }
        StrBuilder sb = new StrBuilder();
        switch (dstContext.getType()) {
            case NAME:
                //英文名
                if (value.matches("[a-zA-Z· ]*")) {
                    String[] split = value.split("[· ]");
                    // 保留名，脱敏姓
                    if (split.length > 1) {
                        for (int i = 1; i < split.length; i++) {
                            value = value.replace(split[i], StrUtil.repeat("*", split[i].length()));
                        }
                    }
                    //保留首字母，其他脱敏
                    else {
                        value = keepSide(value, 1, 0);
                    }
                } else if (value.length() > 2) {
                    value = keepSide(value, 1, 1);
                } else if (value.length() == 2) {
                    value = keepSide(value, 1, 0);
                } else {
                    value = "*";
                }
                break;
            case ID_CARD:
                //军人证件
                if (value.matches("[\\u4e00-\\u9fa5]+[0-9a-zA-Z]+[\\u4e00-\\u9fa5]+")){
                    String patternStr = "([\\u4e00-\\u9fa5]+?)([0-9a-zA-Z]+?)([\\u4e00-\\u9fa5]+)";
                    Pattern compile = Pattern.compile(patternStr, Pattern.DOTALL);
                    Matcher matcher = compile.matcher(value);
                    while (matcher.find()) {
                        value = sb.append(matcher.group(1))
                                .append(keepSide(matcher.group(2),0,2))
                                .append(matcher.group(3) )
                                .toStringAndReset();
                    }
                }
                //港澳居民来往内地通行证
                else if(value.matches("^[HM][0-9]{10}$")){
                    value = keepSide(value,3,2);
                }
                //港澳身份证
                else if(value.matches("^[a-zA-Z][0-9]{6}.[a-zA-Z].$")){
                    value = keepSide(value,2,3);
                }
                //匹配18位身份证
                else if (value.length() == 18) {
                    value = keepSide(value, 10, 2);
                }
                //匹配15位身份证
                else if( value.length() == 15){
                    value = keepSide(value, 8, 2);
                }
                // 其他，大于4时，保留前后两位
                else if (value.length() >4) {
                    value = keepSide(value, 2, 2);
                }
                // 其他，大于2时，保留首位
                else if (value.length() >1) {
                    value = keepSide(value, 1, 0);
                }
                // 其他, 一位字符全脱敏
                else {
                    value = keepSide(value, 0, 0);
                }
                break;

            case MOBILE:
                //大陆手机号
                if(value.matches("^((\\+86|0086) *- *)?[0-9]{11}$")){
                    String patternStr = "(00|\\+)?(86 *- *)?([0-9]{11})";
                    Pattern compile = Pattern.compile(patternStr, Pattern.DOTALL);
                    Matcher matcher = compile.matcher(value);
                    while (matcher.find()) {
                        value = sb.append(StrUtil.nullToDefault(matcher.group(1),StrUtil.EMPTY))
                                .append(StrUtil.nullToDefault(matcher.group(2),StrUtil.EMPTY))
                                .append(keepSide(matcher.group(3),3,4))
                                .toStringAndReset();
                    }
                }
                //香港、澳门 手机号
                else if (value.matches("^((00852|852) *- *)?9[0-9]{7}$")) {
                    String patternStr = "(00)?(852 *- *)?(9[0-9]{7})";
                    Pattern compile = Pattern.compile(patternStr, Pattern.DOTALL);
                    Matcher matcher = compile.matcher(value);
                    while (matcher.find()) {
                        value = sb.append(StrUtil.nullToDefault(matcher.group(1),StrUtil.EMPTY))
                                .append(StrUtil.nullToDefault(matcher.group(2),StrUtil.EMPTY))
                                .append(keepSide(matcher.group(3),2,2))
                                .toStringAndReset();
                    }
                }
                //台湾
                else if (value.matches("((00886|886) *- *)?0?[0-9]{9}")) {
                    String patternStr = "(00)?(886 *- *)?(0)?([0-9]{9}?)";
                    Pattern compile = Pattern.compile(patternStr, Pattern.DOTALL);
                    Matcher matcher = compile.matcher(value);
                    while (matcher.find()) {
                        value = sb.append(StrUtil.nullToDefault(matcher.group(1),StrUtil.EMPTY))
                                .append(StrUtil.nullToDefault(matcher.group(2),StrUtil.EMPTY))
                                .append(keepSide(matcher.group(4),2,3))
                                .toStringAndReset();

                    }
                }
                break;
            case EMAIL:
                if (value.contains("@")) {
                    String prefix = value.substring(0, value.indexOf("@"));
                    String suffix = value.substring(value.indexOf("@"));
                    prefix = (prefix.length()>3 ? prefix.substring(0,3) :prefix) + "***";
                    value = sb.append(prefix)
                            .append(suffix)
                            .toStringAndReset();
                }
                break;
            case ADDRESS:
                {
                    //只显示省市区县 ，详细地址屏蔽
                    String patternStr = "([^省]*省)?(.*自治区|北京市|上海市|天津市|重庆市+)?([^市]*市)?([^区]*区)?([^县]*县)?(.*)";
                    Pattern compile = Pattern.compile(patternStr, Pattern.DOTALL);
                    Matcher matcher = compile.matcher(value);
                    while (matcher.find()) {
                        if(matcher.group() != null){
                            if(StrUtil.isNotBlank(matcher.group())){
                                value = sb.append(StrUtil.nullToDefault(matcher.group(1),StrUtil.EMPTY)) //省
                                        .append(StrUtil.nullToDefault(matcher.group(2),StrUtil.EMPTY))//自治区|北京市|上海市|天津市|重庆市
                                        .append(StrUtil.nullToDefault(matcher.group(3),StrUtil.EMPTY)) //市
                                        .append(StrUtil.nullToDefault(matcher.group(4),StrUtil.EMPTY))//区
                                        .append(StrUtil.nullToDefault(matcher.group(5),StrUtil.EMPTY)) //县
                                        .append( keepSide(matcher.group(6),0,0) )
                                        .toStringAndReset();
                            }
                        }
                    }
                }

                break;
            case BANK_CARD:
                if (value.length() <= 10) {
                    value = keepSide(value, 4, 0);
                } else {
                    value = keepSide(value, 6, 4);
                }
                break;
            case BANK_NAME:
                {
                    String patternStr = "(.*(银行|信用合作联社|信用联社|财政局)+)?([^司]*(股份有限公司|有限责任公司|有限公司)+)?([^行|社]*(支行|分行|分社|信用社)+)?([^社|行|处|部|室|库]*(社|行|处|部|室|库)+)?(.*)";
                    Pattern compile = Pattern.compile(patternStr, Pattern.DOTALL);
                    Matcher matcher = compile.matcher(value);
                    while (matcher.find()) {
                        if(StrUtil.isNotEmpty(matcher.group())){
                            String group1 = matcher.group(1);
                            String group2 = matcher.group(2);
                            String group3 = matcher.group(3);
                            String group4 = matcher.group(4);
                            String group5 = matcher.group(5);
                            String group6 = matcher.group(6);
                            String group7 = matcher.group(7);
                            String group8 = matcher.group(8);
                            String group9 = matcher.group(9);
                            if(StrUtil.isNotEmpty(group1)){
                                sb.append(keepSide(group1.substring(0, group1.lastIndexOf(group2)),1,0)).append(group2);
                            }
                            if(StrUtil.isNotEmpty(group3)){
                                sb.append(keepSide(group3.substring(0, group3.lastIndexOf(group4)),1,0)).append(group4);
                            }
                            if(StrUtil.isNotEmpty(group5)){
                                sb.append(keepSide(group5.substring(0, group5.lastIndexOf(group6)),1,0)).append(group6);
                            }
                            if(StrUtil.isNotEmpty(group7)){
                                sb.append(keepSide(group7.substring(0, group7.lastIndexOf(group8)),1,0)).append(group8);
                            }
                            sb.append(keepSide(group9,group9.length(),0));
                        }
                    }
                    value = sb.toStringAndReset();
                }
                break;
            case BANK_LINE_NUM:
                value = keepSide(value, 2, 0);
                break;
            case PHONE:
                if (value.matches("([0-9]{3} *- *)?[0-9]{7,8}( *- *[0-9]*)?")) {
                    String patternStr = "([0-9]{3} *- *)?([0-9]{7,8})( *- *[0-9]*)?";
                    Pattern compile = Pattern.compile(patternStr, Pattern.DOTALL);
                    Matcher matcher = compile.matcher(value);
                    while (matcher.find()) {
                        value = sb.append(StrUtil.nullToDefault(matcher.group(1),StrUtil.EMPTY))
                                .append(keepSide(matcher.group(2),4,0,true))
                                .append(StrUtil.nullToDefault(matcher.group(3),StrUtil.EMPTY))
                                .toStringAndReset();
                    }
                }
                break;
            case LICENSE_PLATE:
                if (value.length() > 5) {
                    value = keepSide(value, 3, 2);
                } else {
                    value = keepSide(value, 1, 2);
                }
                break;
            case PASSWORD:
                value = keepSide(value, 0, 0);
                break;
            case WECHAT:
                value = keepSide(value, 1, 1);
                break;
            case WORK_UNIT:
                {
                    String patternStr = "(.*(股份有限公司|有限责任公司|有限公司|银行|机构)+)?([^司|心|行|院|园|学]*(公司|中心|支行|分行|院|园|学)+)?([^社|行|局|厅|处|部|所]*(社|行|局|厅|处|部|所)+)?(.*)";
                    Pattern compile = Pattern.compile(patternStr, Pattern.DOTALL);
                    Matcher matcher = compile.matcher(value);
                    while (matcher.find()) {
                        if(StrUtil.isNotEmpty(matcher.group())){
                            String group1 = matcher.group(1);
                            String group2 = matcher.group(2);
                            String group3 = matcher.group(3);
                            String group4 = matcher.group(4);
                            String group5 = matcher.group(5);
                            String group6 = matcher.group(6);
                            String group7 = matcher.group(7);
                            if(StrUtil.isNotEmpty(group1)){
                                sb.append(keepSide(group1.substring(0, group1.lastIndexOf(group2)),2,0)).append(group2);
                            }
                            if(StrUtil.isNotEmpty(group3)){
                                sb.append(keepSide(group3.substring(0, group3.lastIndexOf(group4)),1,0)).append(group4);
                            }
                            if(StrUtil.isNotEmpty(group5)){
                                sb.append(keepSide(group5.substring(0, group5.lastIndexOf(group6)),1,0)).append(group6);
                            }
                            sb.append(keepSide(group7,group7.length(),0));
                        }
                    }
                    value = sb.toStringAndReset();
                }
                break;
            default:
                if (value.length() >5) {
                    value = keepSide(value, 3, 2);
                } else if(value.length()>2){
                    value = keepSide(value, 1, 1);
                } else {
                    value = keepSide(value, 0, 0);
                }
                break;
        }
        return maskSpecChar(value);
    }


    private static JevokitDesensitizationProperties getProperties() {
        return SpringUtil.getBean(JevokitDesensitizationProperties.class);
    }

    /**
     * 保留明文部分（当长度不满足预设值时，全部脱敏）
     * @param val 明文
     * @param preLength 保留前x位
     * @param afterLength 保留后x位
     * @return 脱敏数据
     */
    public static String keepSide(String val, int preLength, int afterLength) {
        return keepSide(val,preLength,afterLength,false);
    }
    /**
     * 保留明文部分（当长度不满足预设值时，全部脱敏）
     * @param val 明文
     * @param preLength 保留前x位
     * @param afterLength 保留后x位
     * @return 脱敏数据
     */
    public static String keepSide(String val, int preLength, int afterLength,boolean negative) {
        if(val != null){
            //符合预期设想，部分脱敏
            if (val.length() > preLength + afterLength) {
                StrBuilder sb = new StrBuilder();
                if(!negative){

                    val = sb.append(val.substring(0, preLength))
                            .append(StrUtil.repeat("*", val.length() - (preLength + afterLength)))
                            .append(val.substring(val.length() - afterLength))
                            .toString();
                } else {
                    val = sb.append(StrUtil.repeat("*", preLength))
                            .append( val.substring(preLength, val.length() - afterLength))
                            .append(StrUtil.repeat("*", afterLength))
                            .toString();

                }
            }
            //不符合预期设想，全部脱敏
            else {
                val = StrUtil.repeat("*", val.length());
            }

        }
        return val;
    }

    /**
     * 解密
     * @param val 加密内容
     * @param ignorePower 是否忽略权限检查
     * @return 返回解密内容
     */
    public static String decrypt(String val,boolean ignorePower){
        Map<String,String> dic = new HashMap<>();
        String patternString = Pattern.quote(DstConst.DESENSITIZED_ENC_KEY + DstConst.DESENSITIZED_ENC_CONTENT_PREFIX) + "(.*?)" + Pattern.quote(DstConst.DESENSITIZED_ENC_CONTENT_SUFFIX);
        Pattern pattern = Pattern.compile(patternString, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(val);
        while (matcher.find()) {
            String enc = matcher.group(1).split("\\" + DstConst.DESENSITIZED_ENC_SPILT)[0];
            String dec = SecurityUtil.sm4cbcDecrypt(enc, sm4Key());
            if(dec.startsWith(DstConst.DESENSITIZED_ENC_LEVEL_PREFIX) && dec.contains(DstConst.DESENSITIZED_ENC_LEVEL_SUFFIX)){
                DstLevel level = DstLevel.of(dec.substring(1, dec.indexOf(DstConst.DESENSITIZED_ENC_LEVEL_SUFFIX)));
                String content = dec.substring(dec.indexOf(DstConst.DESENSITIZED_ENC_LEVEL_SUFFIX)+1);
                if(ignorePower || DesHelper.checkPower(level)){
                    dic.put(matcher.group(0), content);
                } else {
                    throw new RuntimeException(String.format("[%s]敏感信息无权限查看",level));
                }
            }

        }
        StringHolder holder = new StringHolder(val);

        dic.forEach( (enc,src)->{
            holder.value = holder.value.replace(enc,src);
        });
        return holder.value;
    }

    public static String desensitizeJson(String data){
        if (JsonUtil.isTypeJSON(data)){
            data = escapeJson(data);
            data = processJsonForStringField(data);
            data = processJsonForArrayField(data);
            data = unescapeJson(data);
        }
        return data;
    }

    /**
     * 处理字符串类型属性
     * @param data
     * @return
     */
    private static String processJsonForStringField(String data) {
        String snapshot = data;
        StrBuilder sb = new StrBuilder();
        String format = "\"%s\"%s:%s\"%s\"";
        //match for "abc":"dfg"
        String patternString = Pattern.quote("\"") + "(.*?)" + Pattern.quote("\"") +"(\\s*?)"+ Pattern.quote(":") + "(\\s*?)"  + Pattern.quote("\"") +"(.*?)"  + Pattern.quote("\"");
        Pattern pattern = Pattern.compile(patternString, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(data);
        while (matcher.find()) {
            if(matcher.groupCount() >=4){
                String group = matcher.group(0);
                String fieldName = matcher.group(1).substring(matcher.group(1).lastIndexOf("\"")+1);
                String value = unescapeJson(matcher.group(4));
                String enc;
                if(JsonUtil.isTypeJSON(value)){
                    enc = processStrJson(value); // 处理json属性中存在直接使用json字符串作为值的情况
                } else {
                    enc = DesUtil.desByProperties(fieldName,value);
                }
                snapshot = collectDataAndReturnRest(snapshot,group,String.format(format,matcher.group(1),matcher.group(2),matcher.group(3),enc),sb);

            }
        }
        data = sb.append(snapshot).toString();
        return data;
    }

    /**
     * 处理字符串类型json
     * @param json
     * @return
     */
    private static String processStrJson(String json){
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
            json = json.replace(str, getStrJsonFormat(i));
        }

        return processStrJsonForStringField(json,1,level);
    }

    /**
     * 获取字符串类型转义占位符
     * @param level
     * @return
     */
    private static String getStrJsonFormat(int level){
        return  String.format(STR_JSON_PLACEHOLDER,level);
    }

    /**
     * 处理字符串json的字符串属性值
     * @param data
     * @param currentDepth
     * @param maxDepth
     * @return
     */
    private static String processStrJsonForStringField(String data, int currentDepth, int maxDepth){
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
                    String value = matcher.group(4);
                    String enc;
                    if(JsonUtil.isTypeJSON(value)){
                        enc = processStrJsonForStringField(unescapeStrJson(value,currentDepth,maxDepth,true),currentDepth+1,maxDepth);
                    } else {
                        value = unescapeStrJson(value,currentDepth,maxDepth,false);
                        enc = DesUtil.desByProperties(fieldName,value);
                    }
                    snapshot = collectDataAndReturnRest(snapshot,group,String.format(format,matcher.group(1),matcher.group(2),matcher.group(3),enc),sb);
                }
            }
            data = sb.append(snapshot).toString();
        }
        return data;
    }

    /**
     * 反转义字符串类型json
     * @param json
     * @param currentDepth
     * @param maxDepth
     * @param onlyNextDepth
     * @return
     */
    private static String unescapeStrJson(String json, int currentDepth, int maxDepth, boolean onlyNextDepth){
        if(onlyNextDepth){
            int nextDepth = currentDepth+1;
            if(nextDepth<=maxDepth){
                String source = StrUtil.padPre("\"", nextDepth * (nextDepth - 1) + 2, '\\');
                json = json.replace(getStrJsonFormat(nextDepth),source);
            }
        } else {
            for(int i=maxDepth;i>currentDepth;i--){
                String source = StrUtil.padPre("\"", i * (i - 1) + 2, '\\');
                json = json.replace(getStrJsonFormat(i),source);
            }
        }
        return json;
    }

    /**
     * 处理单层数组类型属性 "field1": [1235,"abc","efg"]
     * @param data 带脱敏数据
     * @return 脱敏数据
     */
    private static String processJsonForArrayField(String data) {
        if (data.contains(JSON_ARRAY_START) && data.contains(JSON_ARRAY_END)) {
            String snapshot = data;
            StrBuilder sb = new StrBuilder();
            String format = "\"%s\"%s"+JSON_ARRAY_START+"%s\"%s\"%s"+JSON_ARRAY_END;
            //match for  "field1": [1235,"abc","efg"]
            String patternString = Pattern.quote("\"") + "(.*?)" + Pattern.quote("\"") + "(\\s*:+\\s*?)"  + Pattern.quote(JSON_ARRAY_START) + "([\\sa-zA-Z,0-9]*?)" +Pattern.quote("\"")+"(.*?)" +Pattern.quote("\"")+ "(\\s*)" + Pattern.quote(JSON_ARRAY_END);
            Pattern pattern = Pattern.compile(patternString, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(data);
            while (matcher.find()) {
                if(matcher.groupCount() >=5){
                    String fieldName = matcher.group(1).substring(matcher.group(1).lastIndexOf("\"")+1);
                    String value = matcher.group(4);
                    String val = processArrayString(fieldName,value);
                    snapshot = collectDataAndReturnRest(snapshot,matcher.group(0), String.format(format, matcher.group(1),matcher.group(2),matcher.group(3),val,matcher.group(5)),sb);
                }
            }
            data = sb.append(snapshot).toString();
        }
        return data;
    }

    /**
     * 处理数组类型属性中的字符串值
     * @param fieldName 属性名
     * @param val 属性值
     * @return 脱敏值
     */
    private static String processArrayString(String fieldName,String val) {
        val = "\"" + val + "\"";
        StrBuilder sb = new StrBuilder();
        boolean isBegin = false;
        while (val.contains("\"")){
            int quoteIdx = val.indexOf("\"");
            if(val.trim().indexOf("\"")==0 ){
                sb.append(val.substring(0,quoteIdx+1));
                isBegin = true;
            } else {
                if(isBegin){
                    String value = unescapeJson(val.substring(0, quoteIdx));
                    String enc = DesUtil.desByProperties(fieldName,value);
                    sb.append(enc);
                    sb.append(val.substring(quoteIdx,quoteIdx+1));
                    isBegin = false;
                } else {
                    sb.append(val.substring(0,quoteIdx+1));
                    isBegin = true;
                }
            }
            val = val.substring(quoteIdx+1);

        }
        return sb.subString(1,sb.length()-1);
    }

    /**
     * 转义json中容易引起报错的字符
     * @param json
     * @return
     */
    private static String escapeJson(String json){
        for(Map.Entry<String,String> entry :  DesUtil.JSON_PLACEHOLDER.entrySet()){
            json = json.replace(entry.getValue(),entry.getKey());
        }
        return json;
    }
    /**
     * 解转义json中容易引起报错的字符
     * @param json
     * @return
     */
    private static  String unescapeJson(String json){
        for(Map.Entry<String,String> entry :  DesUtil.JSON_PLACEHOLDER.entrySet()){
            json = json.replace(entry.getKey(),entry.getValue());
        }
        return json;
    }

    /**
     * 脱敏xml类型字符串
     * @param data 待脱敏内容
     * @return 脱敏内容
     */
    public static String desensitizeXml(String data) {
        if(XmlUtil.isXml(data)) {
            data = desensitizeXmlForCData(data);
            data = desensitizeXmlForNormal(data);
        }
        return data;
    }

    /**
     * 脱敏xml类型字符串
     * @param data 待脱敏内容
     * @return 脱敏内容
     */
    private static String desensitizeXmlForNormal(String data) {
        String snapshot = data;
        StrBuilder sb = new StrBuilder();
        String format = "%s<%s>%s</%s>";
        String patternString = "(\\s*?)" +Pattern.quote("<") + "(.*?)" + Pattern.quote(">") + "([^<]*?)" + Pattern.quote("</") + "(.*?)" + Pattern.quote(">");
        Pattern pattern = Pattern.compile(patternString, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(data);

        while (matcher.find()) {
            String fieldName = matcher.group(2);
            String val = matcher.group(3);
            if (fieldName.contains("<")){
                fieldName = fieldName.substring(fieldName.lastIndexOf("<")+1);
            }
            if(StrUtil.isNotBlank(val) && StrUtil.isNotBlank(fieldName)){
                String encVal = DesUtil.desByProperties(fieldName.trim(),val);
                String group = matcher.group(0);
                snapshot = collectDataAndReturnRest(snapshot,group,String.format(format,matcher.group(1),matcher.group(2),encVal,matcher.group(4)),sb);
            }
        }
        data = sb.append(snapshot).toString();

        return data;
}

    /**
     * 数据拼接
     * @param snapshot 数据快照
     * @param subStr 字串
     * @param subStrAfterProcess 处理后的字串
     * @param collector 拼接器
     * @return 返回剩余数据快照
     */
    private static String collectDataAndReturnRest(String snapshot,String subStr,String subStrAfterProcess,StrBuilder collector){
        int subStrIdx = snapshot.indexOf(subStr);
        collector.append(snapshot.substring(0,subStrIdx));
        if(isSimilarEnc(subStrAfterProcess) || isSimilarStrJsonPlaceHolder(subStr)){
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
     * 是否字符串类型json的占位符
     * @param val 内容
     * @return 返回布尔值
     */
    private static boolean isSimilarStrJsonPlaceHolder(String val){
        return StrUtil.isNotBlank(val) && (val.contains(STR_JSON_PLACEHOLDER_PREFIX));
    }
    private static String desensitizeXmlForCData(String data) {
        if(data.contains(XML_C_DATA_START)){
            String snapshot = data;
            StrBuilder sb = new StrBuilder();
            String format = "<%s>%s"+XML_C_DATA_START+"%s"+XML_C_DATA_END+"%s</%s>";
            String patternString = Pattern.quote("<") + "(.*?)" + Pattern.quote(">") + "(\\s*?)" +  Pattern.quote(XML_C_DATA_START)+ "(.*?)" + Pattern.quote(XML_C_DATA_END)+  "(\\s*?)" + Pattern.quote("</") + "(.*?)" + Pattern.quote(">");
            Pattern pattern = Pattern.compile(patternString, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(data);
            while (matcher.find()) {
                String fieldName = matcher.group(1);
                String val = matcher.group(3);
                if (fieldName.contains("<")){
                    fieldName = fieldName.substring(fieldName.lastIndexOf("<")+1);
                }
                if(StrUtil.isNotBlank(val) && StrUtil.isNotBlank(fieldName)){
                    String encVal = DesUtil.desByProperties(fieldName.trim(),val);
                    snapshot = collectDataAndReturnRest(snapshot,matcher.group(0),String.format(format,matcher.group(1),matcher.group(2),encVal,matcher.group(4),matcher.group(5)),sb);
                }

            }
            data = sb.append(snapshot).toString();
        }
        return data;
    }

    /**
     * 脱敏数据（指定处理类型 view/log）
     * @param processType 处理类型 view/log
     * @param callback 回调函数，自定义逻辑
     * @return 脱敏数据
     * @param <T> 泛型
     */
    public static <T>T desByProcessTypeWrap(DstProcessType processType, CallbackR<T> callback) {

        try {
            PROCESS_TYPE.set(processType);
            return callback.call();
        } finally {
            PROCESS_TYPE.remove();
        }
    }
    /**
     * 脱敏数据（指定处理类型 view/log）
     * @param processType 处理类型 view/log
     * @param callback 回调函数，自定义逻辑
     */
    public static void desByProcessTypeWrap(DstProcessType processType, Callback callback) {

        try {
            PROCESS_TYPE.set(processType);
            callback.call();
        } finally {
            PROCESS_TYPE.remove();
        }
    }

    /**
     * 判断是否位日志类型
     * @return 返回布尔值
      */
    private static boolean isProcessLog() {
        return PROCESS_TYPE.get() == DstProcessType.LOG;
    }
}
