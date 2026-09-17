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
 * 脱敏核心工具：脱敏执行（BASIC 掩码 / SM4 密文 / MIXED 混合）、属性名规则匹配、
 * 密文解密还原，以及 JSON/XML 报文的正则级脱敏。
 * 使用方式：注解链路（@DstProperty → DesensitizationAspect）与属性名规则链路
 * （{@link #desByProperties(String, String)}，规则源 spkit.desensitization.prop-pattern）最终都收敛到
 * {@link #des(String, DstContext)}；JSON/XML 原文脱敏用 {@link #desensitizeJson(String)} /
 * {@link #desensitizeXml(String)}；密文还原用 {@link #decrypt(String, boolean)}。
 * <p>命中与输出语义（关键约束）：
 * <ul>
 *   <li>幂等：已带 {@code @DST} 密文前缀的值直接跳过，不二次脱敏；</li>
 *   <li>算法选择：视图/日志两条链路独立取配置（{@link #desByProcessTypeWrap} 切换 LOG 档），
 *       未配置时降级 BASIC；SM4 密钥取 spkit.crypto.sm4-key，缺失 fail-fast；</li>
 *   <li>权限：DECRYPT 档（默认）在 {@link #decrypt(String, boolean)} 按密文内嵌级别校验
 *       （DesHelper.checkPower），无权限抛异常拒绝还原；ENCRYPT 档在脱敏出口校验，有权限直接放行明文；</li>
 *   <li>容错：BASIC 掩码对不识别的格式（如非三地手机号）原样返回——宁漏脱敏不阻断业务；
 *       属性名规则匹配失败仅告警不抛错。</li>
 * </ul>
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


    /**
     * 获取（并缓存）自定义脱敏器实例。
     *
     * @param clazz 脱敏器实现类；必须保留 public 无参构造（反射实例化）
     * @return 该类的共享实例（Class 级缓存，线程安全，整个 JVM 生命周期复用同一实例——实现应保持无状态）
     * @throws RuntimeException 实例化失败（无参构造缺失/不可访问）时抛出，属装配期错误应修复而非捕获
     */
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
     * 按属性名规则脱敏（无注解场景的入口，如 JSON/XML 原文脱敏的每字段回调用它）。
     *
     * @param propName 属性名；支持全限定名（取最后一段与规则匹配，除非正则含转义点号要求全名）；null 时原样返回
     * @param value    属性值；命中规则则脱敏，未命中/被 ignore-pattern 命中时原样返回（不抛错）
     * @return 脱敏后的值（形态由当前算法决定，见 {@link #des(String, DstContext)}）；规则匹配失败仅告警不阻断
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
     * 脱敏执行总入口：按上下文的类型/级别与当前算法/权限档位处理单个值。
     * <p>跳过条件（防御性，均原样返回）：value 为 null、value 已带 {@code @DST} 密文前缀（幂等，防二次脱敏）、
     * dstContext 为 null 或其 type 为 null；ENCRYPT 档且当前用户有该级别明文权限（出口放行原文）。
     *
     * @param value      待脱敏值
     * @param dstContext 脱敏上下文（类型决定掩码规则，级别决定密文内嵌权限档）
     * @return BASIC 返回掩码串；SM4 返回 {@code @DST([LV*]密文)}；MIXED 返回密文+掩码组合；跳过时返回原值
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
     * 获取当前线程应使用的脱敏算法。
     *
     * @return 处于 {@code desByProcessTypeWrap(LOG)} 包裹内时返回 algorithm.log 配置，否则 algorithm.view；
     *         配置不可用（容器未就绪）时降级 BASIC（掩码最安全，密文依赖 SM4 密钥存在）
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
     * 处理混合脱敏（基础掩码部分由本方法现算）。
     *
     * @param value      明文
     * @param dstContext 脱敏上下文（类型决定掩码规则；级别为空时按 LV9 内嵌密文）
     * @return 形如 {@code @DST([LV*]SM4密文|基础掩码)} 的组合串——密文供有权限者还原，掩码供人快速浏览
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
     * 处理混合脱敏（显式传入基础掩码值，避免对同值重复掩码计算）。
     *
     * @param value      明文
     * @param basicValue 已算好的基础脱敏值；空白时内部按 dstContext 现算。会做特殊字符掩码
     *                   （{@code )[]} 替换为 {@code *}，防破坏 MIXED 信封的括号/分隔符语法）
     * @param dstContext 脱敏上下文（级别为空时按 LV9 内嵌密文）
     * @return 形如 {@code @DST([LV*]SM4密文|基础掩码)} 的组合串
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
     * 国密 SM4-CBC 可逆脱敏。
     *
     * @param value      明文
     * @param dstContext 脱敏上下文（级别为空时按 LV9 内嵌，解密还原时依档位校验权限）
     * @return 形如 {@code @DST([LV*]密文)} 的信封串；密钥取 spkit.crypto.sm4-key
     * @throws IllegalStateException SM4 密钥未配置时 fail-fast（密文无密钥不可解，宁失败不产出坏数据）
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
     * 基础掩码脱敏：按 {@link DstType} 的形态规则把敏感段替换为 {@code *}，不可逆。
     * <p>各类型保留形态（保留=明文，其余掩码）：
     * <ul>
     *   <li>NAME：中文姓名保姓（≥3 字保首尾各 1 字，2 字保首字，单字全掩）；英文名按空格/间隔号分段保名掩姓；
     *       单段英文名保首字母</li>
     *   <li>ID_CARD：18 位保前 10 后 2；15 位保前 8 后 2；军人证件保留中文首尾段、数字段保后 2；
     *       港澳通行证（H/M 开头 10 位）保前 3 后 2；港澳身份证保前 2 后 3；其他长度分段保留首尾</li>
     *   <li>MOBILE：大陆 11 位保前 3 后 4（+86/0086 前缀原样保留）；港澳 9 开头 8 位保前 2 后 2；
     *       台湾 9 位保前 2 后 3；<b>不匹配三地格式时原样返回（宁漏脱敏不阻断，见类注释）</b></li>
     *   <li>EMAIL：本地部分保前 3 位 + 固定 {@code ***}，@ 与域名完整保留</li>
     *   <li>ADDRESS：省/自治区/市/区/县逐级匹配保留，其余详细地址全掩</li>
     *   <li>BANK_CARD：位数 ≤10 保前 4；长卡号保前 6 后 4</li>
     *   <li>BANK_NAME / WORK_UNIT：按机构后缀词（银行/有限公司/支行 等）切分，保后缀词原文与各段首字</li>
     *   <li>BANK_LINE_NUM：保前 2 位，其余掩码</li>
     *   <li>PHONE：区号与分机号保留，号码主体掩前 4 位保后段</li>
     *   <li>LICENSE_PLATE：&gt;5 位保前 3 后 2；短牌保前 1 后 2</li>
     *   <li>PASSWORD：全掩；WECHAT：保首尾各 1 位</li>
     *   <li>DEFAULT：&gt;5 位保 3+2；&gt;2 位保 1+1；其余全掩</li>
     * </ul>
     *
     * @param value      明文数据
     * @param dstContext 脱敏对象上下文
     * @return 掩码后的数据（可能含特殊字符掩码，见 maskSpecChar）；value 为 null、或 dstContext/type 为 null 时原样返回（不脱敏不抛错）
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


    /** 取容器中的脱敏配置（静态方法惰性查找，容器未就绪返回 null 由调用方降级）。 */
    private static JevokitDesensitizationProperties getProperties() {
        return SpringUtil.getBean(JevokitDesensitizationProperties.class);
    }

    /**
     * 保留明文首尾、中间掩码（正方向）。
     *
     * @param val         明文；null 时原样返回
     * @param preLength   保留前 x 位
     * @param afterLength 保留后 x 位
     * @return 长度 &gt; preLength+afterLength 时返回"保留前段 + 星号中段 + 保留后段"；长度不足预设时全掩
     *         （宁可多掩不泄露——短值保留首尾反而暴露大比例明文）
     */
    public static String keepSide(String val, int preLength, int afterLength) {
        return keepSide(val,preLength,afterLength,false);
    }
    /**
     * 保留明文中段、首尾掩码（反方向，用于"头部才是敏感段"的场景，如固话号码主体）。
     *
     * @param val         明文；null 时原样返回
     * @param preLength   掩码前 x 位
     * @param afterLength 掩码后 x 位
     * @param negative    true 启用反向掩码（前段星号 + 保留中段 + 后段星号）；长度不足预设时全掩
     * @return 反向掩码后的数据
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
     * 解密文本中的全部 {@code @DST(...)} 密文为明文（就地替换，非密文部分不动）。
     *
     * @param val        含密文的文本；无密文时原样返回
     * @param ignorePower true 跳过权限校验（内部/运维场景）；false 按密文内嵌级别校验
     * @return 全部密文还原后的文本；密文级别前缀缺失/无法解析时该段保持原样（防御性跳过）
     * @throws RuntimeException 密文级别超出当前用户权限时抛出（拒绝还原，防越权查看明文）；
     *                           SM4 解密失败（密钥不符/密文损坏）由底层抛出
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

    /**
     * 对 JSON 原文做属性名规则脱敏（不依赖实体类注解，直接在字符串层面处理）。
     * <p>处理规则：先转义易冲突字符（{@code \"} 占位符化），再逐属性匹配
     * （"key":"value" 形态，按属性名走 {@link #desByProperties}），数组值（"key":["a","b"]）逐元素处理，
     * 属性值为 JSON 字符串的场景（值本身是转义后的 JSON）递归同规则处理，最后还原转义。
     *
     * @param data JSON 文本；非合法 JSON（JsonUtil.isTypeJSON 判定）时原样返回
     * @return 属性名命中规则则替换值后的 JSON 文本；结构不变，仅值变化
     */
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
     * 对 XML 原文做标签名规则脱敏：普通文本节点（{@code <tag>值</tag>}）与 CDATA 节点
     * （{@code <tag><![CDATA[值]]></tag>}）均按标签名走 {@link #desByProperties} 脱敏。
     *
     * @param data XML 文本；非合法 XML（XmlUtil.isXml 判定）时原样返回
     * @return 标签名命中规则则节点值被替换后的 XML 文本；结构不变，仅文本节点值变化
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
    /**
     * 脱敏 CDATA 节点：先处理 CDATA（防其内容被普通节点正则误切），返回后再由
     * desensitizeXmlForNormal 处理普通文本节点——两段式是为了让 CDATA 边界不被普通节点模式吞掉。
     */
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
     * 在指定处理类型（VIEW/LOG）下执行回调：回调内所有脱敏取 log/view 对应链路的算法配置。
     * <p>用于日志链路（logback/LogBuilder）包住输出逻辑，使其脱敏走 algorithm.log 而非 algorithm.view。
     *
     * @param processType 处理类型；LOG 取日志链路算法，VIEW 取视图链路算法
     * @param callback    回调函数，自定义逻辑
     * @return 回调返回值（透传，本方法不加工）
     * @param <T> 回调返回类型
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
     * 在指定处理类型（VIEW/LOG）下执行无返回值回调（语义同 {@link #desByProcessTypeWrap(DstProcessType, CallbackR)}）。
     *
     * @param processType 处理类型；LOG 取日志链路算法，VIEW 取视图链路算法
     * @param callback    回调函数，自定义逻辑
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
