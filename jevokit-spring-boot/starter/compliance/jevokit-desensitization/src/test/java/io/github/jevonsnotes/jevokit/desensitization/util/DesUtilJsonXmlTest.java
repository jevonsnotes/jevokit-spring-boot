package io.github.jevonsnotes.jevokit.desensitization.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.github.jevonsnotes.jevokit.desensitization.DesensitizationTestSupport;
import io.github.jevonsnotes.jevokit.desensitization.config.JevokitDesensitizationProperties;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstLevel;
import io.github.jevonsnotes.jevokit.desensitization.consts.DstType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DesUtil.desensitizeJson / desensitizeXml：
 * 小样例矩阵（嵌套/数组/转义/字符串套 JSON/CDATA）+ 抢救 fixtures 大报文回归。
 */
class DesUtilJsonXmlTest {

    private static final String MOBILE = "13800138000";
    private static final String ID_CARD = "110101190001011234";

    private static JevokitDesensitizationProperties sensitiveProps() {
        Map<DstType, List<String>> lv0 = new HashMap<>();
        lv0.put(DstType.MOBILE, Arrays.asList("mobile", "phone", "aae005"));
        lv0.put(DstType.NAME, Arrays.asList("name", "aac003", "paf313", "pkc247", "oppactname", "Name"));
        lv0.put(DstType.ID_CARD, Arrays.asList("idNumber", "aac002", "ID", "PersonalID"));
        lv0.put(DstType.BANK_CARD, Arrays.asList("pka575", "BankAccount"));
        return DesensitizationTestSupport.properties(
                Collections.singletonMap(DstLevel.LV0, lv0),
                Collections.emptyList(),
                Collections.emptyMap());
    }

    // ------------------------- desensitizeJson：小样例 -------------------------

    @Test
    @DisplayName("[真] JSON 顶层字段脱敏 + 数值/布尔字段不动")
    void jsonSimpleFields() {
        DesensitizationTestSupport.minimalRunner(sensitiveProps()).run(context -> {
            String out = DesUtil.desensitizeJson(
                    "{\"name\":\"张三丰\",\"mobile\":\"13800138000\",\"count\":123,\"flag\":false}");
            assertThat(out).as("name 脱敏").contains("张*丰");
            assertThat(out).as("mobile 脱敏").contains("138****8000").doesNotContain(MOBILE);
            assertThat(out).as("数值字段保留").contains("\"count\":123");
            assertThat(out).as("布尔字段保留").contains("\"flag\":false");
        });
    }

    @Test
    @DisplayName("[真] JSON 嵌套对象字段脱敏")
    void jsonNestedObject() {
        DesensitizationTestSupport.minimalRunner(sensitiveProps()).run(context -> {
            String out = DesUtil.desensitizeJson(
                    "{\"applicantInfo\":{\"mobile\":\"13800138000\",\"city\":\"北京\"}}");
            assertThat(out).as("嵌套 mobile 脱敏").doesNotContain(MOBILE).contains("138****8000");
            assertThat(out).as("非敏感嵌套字段保留").contains("北京");
        });
    }

    @Test
    @DisplayName("[真] JSON 字符串数组逐项脱敏")
    void jsonArrayField() {
        DesensitizationTestSupport.minimalRunner(sensitiveProps()).run(context -> {
            String out = DesUtil.desensitizeJson("{\"phone\":[\"13800138000\",\"13900139000\"]}");
            assertThat(out).as("数组内手机号全部脱敏")
                    .doesNotContain(MOBILE)
                    .doesNotContain("13900139000")
                    .contains("138****8000")
                    .contains("139****9000");
        });
    }

    @Test
    @DisplayName("[真] JSON 转义引号值内的敏感数据脱敏（%dst_ph@quote% 占位机制）")
    void jsonEscapedQuotesValue() {
        DesensitizationTestSupport.minimalRunner(sensitiveProps()).run(context -> {
            String out = DesUtil.desensitizeJson("{\"name\":\"张\\\",\"三\",\"remark\":\"ok\"}");
            assertThat(out).as("含转义引号的 name 不崩溃且明文姓名散列被掩码处理").contains("*");
        });
    }

    @Test
    @DisplayName("[真] JSON 属性值本身是 JSON 字符串（字符串套 JSON）递进脱敏")
    void jsonStringValuedJson() {
        DesensitizationTestSupport.minimalRunner(sensitiveProps()).run(context -> {
            String out = DesUtil.desensitizeJson("{\"payload\":\"{\\\"mobile\\\":\\\"" + MOBILE + "\\\"}\"}");
            assertThat(out).as("字符串内嵌 JSON 的手机号必须脱敏").doesNotContain(MOBILE);
        });
    }

    @Test
    @DisplayName("[假] 非 JSON 文本原样返回")
    void jsonNonJsonPassthrough() {
        DesensitizationTestSupport.minimalRunner(sensitiveProps()).run(context -> {
            assertThat(DesUtil.desensitizeJson("普通中文文本，不是 JSON"))
                    .as("纯文本不处理")
                    .isEqualTo("普通中文文本，不是 JSON");
            // 抢救 fixtures：非法 JSON 形态（原 SimpleTest.json2 草稿样例）
            String malformed = DesensitizationTestSupport.fixture("malformed-array.json");
            assertThat(DesUtil.desensitizeJson(malformed.trim()))
                    .as("非法 JSON（[\"abc\":123,...]）不进入脱敏流程")
                    .isEqualTo(malformed.trim());
        });
    }

    // ------------------------- desensitizeJson：fixtures 大报文 -------------------------

    @Test
    @DisplayName("[真] fixtures/insurance-policy.json：敏感字段清除、骨架与非敏感字段保留")
    void insurancePolicyFixture() {
        DesensitizationTestSupport.minimalRunner(sensitiveProps()).run(context -> {
            String source = DesensitizationTestSupport.fixture("insurance-policy.json");
            assertThat(source).as("fixture 前置自检：包含明文敏感数据").contains(MOBILE).contains(ID_CARD);

            String out = DesUtil.desensitizeJson(source);
            assertThat(out).as("手机号必须全部脱敏").doesNotContain(MOBILE);
            assertThat(out).as("身份证号必须全部脱敏").doesNotContain(ID_CARD);
            assertThat(out).as("非敏感字段保留：业务单元编码").contains("BUC110000DM1_01");
            assertThat(out).as("非敏感字段保留：支付类型").contains("\"payType\": \"C\"");
            assertThat(out).as("非敏感字段保留：金额数值").contains("\"sumAmount\": 3000000");
            assertThat(out).as("报文骨架保留：requestHead").contains("\"requestHead\"");
            assertThat(out).as("报文骨架保留：insuredInfos 数组").contains("\"insuredInfos\"");
        });
    }

    // ------------------------- desensitizeXml：小样例 -------------------------

    @Test
    @DisplayName("[真] XML 元素按标签名脱敏，结构保留")
    void xmlSimpleElements() {
        DesensitizationTestSupport.minimalRunner(sensitiveProps()).run(context -> {
            String out = DesUtil.desensitizeXml("<root><Name>张三丰</Name><ID>" + ID_CARD
                    + "</ID><City>湛江市</City></root>");
            assertThat(out).as("Name 标签脱敏").contains("张*丰");
            assertThat(out).as("ID 标签脱敏").doesNotContain(ID_CARD);
            assertThat(out).as("非敏感标签保留").contains("湛江市");
            assertThat(out).as("标签结构保留").contains("<root>").contains("</root>");
        });
    }

    @Test
    @DisplayName("[真] XML CDATA 节段按标签名脱敏")
    void xmlCDataDesensitized() {
        JevokitDesensitizationProperties properties = DesensitizationTestSupport.properties(
                Collections.singletonMap(DstLevel.LV0, Collections.singletonMap(
                        DstType.MOBILE, Collections.singletonList("payload"))),
                Collections.emptyList(),
                Collections.emptyMap());
        DesensitizationTestSupport.minimalRunner(properties).run(context -> {
            String out = DesUtil.desensitizeXml("<payload><![CDATA[" + MOBILE + "]]></payload>");
            assertThat(out).as("CDATA 内手机号必须脱敏").doesNotContain(MOBILE).contains("138****8000");
            assertThat(out).as("CDATA 结构保留").contains("<![CDATA[").contains("]]>");
        });
    }

    @Test
    @DisplayName("[假] CDATA 内容无匹配规则时原样保留")
    void xmlCDataPassthroughWhenNoPattern() {
        DesensitizationTestSupport.minimalRunner(sensitiveProps()).run(context -> {
            String xml = "<payload><![CDATA[{\"remark\":\"普通\"}]]></payload>";
            assertThat(DesUtil.desensitizeXml(xml)).as("无规则命中的 CDATA 原样返回").isEqualTo(xml);
        });
    }

    @Test
    @DisplayName("[假] 非 XML 文本原样返回")
    void xmlNonXmlPassthrough() {
        DesensitizationTestSupport.minimalRunner(sensitiveProps()).run(context ->
                assertThat(DesUtil.desensitizeXml("普通中文文本，不是 XML"))
                        .isEqualTo("普通中文文本，不是 XML"));
    }

    // ------------------------- desensitizeXml：fixtures 大报文 -------------------------

    @Test
    @DisplayName("[真] fixtures/upload-med-result.xml：证件号清除、报文骨架保留")
    void uploadMedResultFixture() {
        DesensitizationTestSupport.minimalRunner(sensitiveProps()).run(context -> {
            String source = DesensitizationTestSupport.fixture("upload-med-result.xml");
            assertThat(source).as("fixture 前置自检：包含明文证件号").contains(ID_CARD);

            String out = DesUtil.desensitizeXml(source);
            assertThat(out).as("身份证号必须全部脱敏").doesNotContain(ID_CARD);
            assertThat(out).as("报文骨架保留").contains("<UploadMedResult_Request>").contains("<PayeeInformation>");
            assertThat(out).as("诊断文本保留（无规则命中）").contains("冠状动脉粥样硬化性心脏病");
        });
    }

    @Test
    @DisplayName("[真] fixtures/soap-init-report.xml：密文域字段（aac002/aae005/aac003/pka575 等）脱敏")
    void soapInitReportFixture() {
        DesensitizationTestSupport.minimalRunner(sensitiveProps()).run(context -> {
            String source = DesensitizationTestSupport.fixture("soap-init-report.xml").trim();
            assertThat(source).as("fixture 前置自检：包含明文手机号").contains(MOBILE).contains(ID_CARD);

            String out = DesUtil.desensitizeXml(source);
            assertThat(out).as("手机号必须脱敏").doesNotContain(MOBILE);
            assertThat(out).as("身份证号必须脱敏").doesNotContain(ID_CARD);
            assertThat(out).as("姓名必须脱敏").doesNotContain("张一三").doesNotContain("李四五").doesNotContain("王六七");
            assertThat(out).as("银行卡号必须脱敏").doesNotContain("6222020000000000000");
            assertThat(out).as("报文骨架保留：reports 结构").contains("<reports>");
        });
    }
}
