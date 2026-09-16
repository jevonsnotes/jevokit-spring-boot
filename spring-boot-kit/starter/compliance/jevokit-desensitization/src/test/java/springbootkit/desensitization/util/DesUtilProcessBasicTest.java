package springbootkit.desensitization.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import springbootkit.desensitization.consts.DstType;
import springbootkit.desensitization.dto.DstContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DesUtil.processBasic 纯函数矩阵：全 DstType × 边界（空串/短串/标准长度/超长）。
 * processBasic 不依赖 Spring（无 properties 读取），可直接静态调用。
 */
class DesUtilProcessBasicTest {

    private static String process(String value, DstType type) {
        return DesUtil.processBasic(value, DstContext.builder().type(type).build());
    }

    // ------------------------- NAME -------------------------

    @Test
    @DisplayName("NAME：中文三字名保首尾脱中间")
    void nameChinese() {
        assertThat(process("张三丰", DstType.NAME)).as("三字中文名").isEqualTo("张*丰");
        assertThat(process("欧阳娜娜", DstType.NAME)).as("四字中文名").isEqualTo("欧**娜");
    }

    @Test
    @DisplayName("NAME：两字名保姓脱名，单字全脱敏")
    void nameShort() {
        assertThat(process("李四", DstType.NAME)).as("两字中文名").isEqualTo("李*");
        assertThat(process("王", DstType.NAME)).as("单字全脱敏").isEqualTo("*");
    }

    @Test
    @DisplayName("NAME：英文名保名脱姓（保留姓以外部分）")
    void nameEnglish() {
        assertThat(process("John Smith", DstType.NAME)).as("英文全名脱敏姓").isEqualTo("John *****");
    }

    @Test
    @DisplayName("NAME：单词英文保首字母")
    void nameEnglishSingleWord() {
        assertThat(process("Jackson", DstType.NAME)).as("单个英文词保首字母").isEqualTo("J******");
    }

    @Test
    @DisplayName("NAME：超长输入不崩溃且保留头尾")
    void nameLong() {
        String longName = "赵".repeat(50) + "钱";
        String masked = process(longName, DstType.NAME);
        assertThat(masked).as("超长姓名长度不变").hasSize(longName.length());
        assertThat(masked).as("保留首尾").startsWith("赵").endsWith("钱").contains("*");
    }

    // ------------------------- ID_CARD -------------------------

    @Test
    @DisplayName("ID_CARD：18 位身份证保前 10 后 2")
    void idCard18() {
        assertThat(process("110101190001011234", DstType.ID_CARD))
                .as("18 位身份证")
                .isEqualTo("1101011900******34");
    }

    @Test
    @DisplayName("ID_CARD：15 位身份证保前 8 后 2")
    void idCard15() {
        assertThat(process("110101900101123", DstType.ID_CARD))
                .as("15 位身份证")
                .isEqualTo("11010190*****23");
    }

    @Test
    @DisplayName("ID_CARD：港澳通行证 H/M 开头 11 位")
    void idCardHmPass() {
        assertThat(process("H1234567890", DstType.ID_CARD))
                .as("H 开头 10 位数字通行证")
                .isEqualTo("H12******90");
    }

    @Test
    @DisplayName("ID_CARD：军人证件（中文+数字+中文）保留头尾中文，数字段脱敏")
    void idCardMilitary() {
        String masked = process("军字第0011901号", DstType.ID_CARD);
        assertThat(masked).as("证件头尾中文保留").startsWith("军字第").endsWith("号");
        assertThat(masked).as("数字段必须脱敏").doesNotContain("0011901");
    }

    @Test
    @DisplayName("ID_CARD：港澳身份证字母数字混合形态")
    void idCardHkMacao() {
        String masked = process("B123456(A)", DstType.ID_CARD);
        assertThat(masked).as("保留前 2 后 3").hasSize(10).startsWith("B1").endsWith("(A)").contains("*");
    }

    @Test
    @DisplayName("ID_CARD：其他长度分段脱敏（>4 保前后各 2；2 保前 1；1 全脱敏）")
    void idCardOtherLengths() {
        assertThat(process("12345", DstType.ID_CARD)).as("5 位保前后各 2").isEqualTo("12*45");
        assertThat(process("12", DstType.ID_CARD)).as("2 位保前 1").isEqualTo("1*");
        assertThat(process("1", DstType.ID_CARD)).as("1 位全脱敏").isEqualTo("*");
    }

    // ------------------------- MOBILE -------------------------

    @Test
    @DisplayName("MOBILE：大陆 11 位保前 3 后 4")
    void mobileMainland() {
        assertThat(process("13800138000", DstType.MOBILE)).isEqualTo("138****8000");
    }

    @Test
    @DisplayName("MOBILE：+86/0086 前缀（连字符形态）保留，本体脱敏")
    void mobileWithCountryPrefix() {
        assertThat(process("+86-13800138000", DstType.MOBILE)).as("+86- 前缀保留").isEqualTo("+86-138****8000");
        assertThat(process("0086-13800138000", DstType.MOBILE)).as("0086- 前缀保留").isEqualTo("0086-138****8000");
    }

    @Test
    @DisplayName("MOBILE：港澳台形态各自脱敏")
    void mobileHkTw() {
        assertThat(process("91234567", DstType.MOBILE)).as("香港 8 位保前 2 后 2").isEqualTo("91****67");
        assertThat(process("0912345678", DstType.MOBILE)).as("台湾形态（首 0 不在保留组，既有行为）").isEqualTo("91****678");
    }

    @Test
    @DisplayName("MOBILE：不匹配任何形态时原样返回（既有 fail-open 行为固化）")
    void mobileNonMatchingUnchanged() {
        assertThat(process("12345", DstType.MOBILE)).as("非手机号形态不处理").isEqualTo("12345");
    }

    // ------------------------- EMAIL -------------------------

    @Test
    @DisplayName("EMAIL：保前 3 + ***，域名保留")
    void email() {
        assertThat(process("zhangsan@example.com", DstType.EMAIL)).isEqualTo("zha***@example.com");
        assertThat(process("ab@c.com", DstType.EMAIL)).as("短前缀整体保留 + ***").isEqualTo("ab***@c.com");
    }

    @Test
    @DisplayName("EMAIL：不含 @ 原样返回")
    void emailWithoutAtUnchanged() {
        assertThat(process("not-an-email", DstType.EMAIL)).isEqualTo("not-an-email");
    }

    // ------------------------- ADDRESS -------------------------

    @Test
    @DisplayName("ADDRESS：省市区保留，详细地址脱敏")
    void addressProvinceCityDistrict() {
        assertThat(process("广东省湛江市麻章区瑞云路11号", DstType.ADDRESS))
                .as("省市区保留 + 详细地址脱敏")
                .isEqualTo("广东省湛江市麻章区******");
    }

    @Test
    @DisplayName("ADDRESS：无省市前缀时仅保留区")
    void addressDistrictOnly() {
        String masked = process("麻章区瑞云路sdflj11号", DstType.ADDRESS);
        assertThat(masked).as("区名保留").contains("麻章区");
        assertThat(masked).as("详细地址必须脱敏").doesNotContain("瑞云路");
    }

    // ------------------------- BANK_CARD / BANK_NAME / BANK_LINE_NUM -------------------------

    @Test
    @DisplayName("BANK_CARD：长卡号保前 6 后 4，短卡号保前 4")
    void bankCard() {
        assertThat(process("6222020000000000000", DstType.BANK_CARD)).as("19 位卡号").isEqualTo("622202*********0000");
        assertThat(process("6222021234", DstType.BANK_CARD)).as("10 位卡号").isEqualTo("6222******");
        assertThat(process("12345", DstType.BANK_CARD)).as("5 位卡号").isEqualTo("1234*");
    }

    @Test
    @DisplayName("BANK_NAME：总行关键词与网点后缀保留，主体脱敏（原 SimpleTest 三样例）")
    void bankName() {
        String masked = process("卓资银行股份有限公司巴音锡勒支行", DstType.BANK_NAME);
        assertThat(masked).as("银行/股份/支行骨架保留").contains("银行").contains("支行");
        assertThat(masked).as("专名必须脱敏").doesNotContain("卓资").doesNotContain("巴音锡勒");

        String financeBureau = process("中山市财政局", DstType.BANK_NAME);
        assertThat(financeBureau).as("财政局关键词保留").contains("财政局");
        assertThat(financeBureau).as("地名必须脱敏").doesNotContain("中山市");

        String creditUnion = process("安新县农村信用联社股份有限公司大张庄信用社", DstType.BANK_NAME);
        assertThat(creditUnion).as("联社/公司/信用社骨架保留").contains("信用联社").contains("信用社");
        assertThat(creditUnion).as("专名必须脱敏").doesNotContain("安新县").doesNotContain("大张庄");
    }

    @Test
    @DisplayName("BANK_LINE_NUM：保前 2")
    void bankLineNum() {
        assertThat(process("123456789012", DstType.BANK_LINE_NUM)).isEqualTo("12**********");
    }

    // ------------------------- PHONE -------------------------

    @Test
    @DisplayName("PHONE：区号与分机保留，号码段前 4 脱敏（negative 保留后半）")
    void phone() {
        assertThat(process("010-1234567", DstType.PHONE)).isEqualTo("010-****567");
        assertThat(process("010-1234567-801", DstType.PHONE)).as("分机号保留").isEqualTo("010-****567-801");
    }

    @Test
    @DisplayName("PHONE：不匹配形态原样返回")
    void phoneNonMatchingUnchanged() {
        assertThat(process("12345", DstType.PHONE)).isEqualTo("12345");
    }

    // ------------------------- 其他类型 -------------------------

    @Test
    @DisplayName("LICENSE_PLATE：保前 3 后 2")
    void licensePlate() {
        assertThat(process("粤B12345", DstType.LICENSE_PLATE)).isEqualTo("粤B1**45");
        assertThat(process("粤B1", DstType.LICENSE_PLATE)).as("短车牌全脱敏").isEqualTo("***");
    }

    @Test
    @DisplayName("PASSWORD：全脱敏")
    void password() {
        assertThat(process("P@ssw0rd!", DstType.PASSWORD)).isEqualTo("*********");
    }

    @Test
    @DisplayName("WECHAT：保首尾各 1")
    void wechat() {
        assertThat(process("wxid_abc123", DstType.WECHAT)).isEqualTo("w*********3");
    }

    @Test
    @DisplayName("WORK_UNIT：公司/机构后缀保留，专名脱敏")
    void workUnit() {
        String masked = process("阿里巴巴股份有限公司", DstType.WORK_UNIT);
        assertThat(masked).as("公司类型后缀保留").contains("公司");
        assertThat(masked).as("企业专名必须脱敏").doesNotContain("阿里巴巴");
    }

    @Test
    @DisplayName("DEFAULT/CUSTOM：默认分段脱敏规则")
    void defaultAndCustom() {
        assertThat(process("abcdefgh", DstType.DEFAULT)).as("8 字符保前 3 后 2").isEqualTo("abc***gh");
        assertThat(process("abcdefgh", DstType.CUSTOM)).as("CUSTOM 走 default 分支").isEqualTo("abc***gh");
        assertThat(process("ab", DstType.DEFAULT)).as("2 字符全脱敏").isEqualTo("**");
    }

    @Test
    @DisplayName("特殊字符 [ ] < > \" \\ 被掩码为 *（防格式注入）")
    void specialCharsMasked() {
        String masked = process("a[b]cdef", DstType.DEFAULT);
        assertThat(masked).as("方括号不得残留在脱敏结果中").doesNotContain("[").doesNotContain("]");
    }

    // ------------------------- 边界扫描 -------------------------

    @ParameterizedTest(name = "[边界] 空串在 {0} 类型下不抛异常")
    @EnumSource(DstType.class)
    void emptyStringSafeForAllTypes(DstType type) {
        assertThat(process("", type))
                .as("空串在 %s 类型下应安全返回", type)
                .isIn("", "*");
    }

    @ParameterizedTest(name = "[边界] 单字符在 {0} 类型下不抛异常且被脱敏/保留")
    @EnumSource(DstType.class)
    void singleCharSafeForAllTypes(DstType type) {
        String masked = process("汉", type);
        assertThat(masked).as("单字符处理不崩溃").isNotNull();
    }

    @Test
    @DisplayName("超长输入（1 万字符）在 DEFAULT 类型下长度不变")
    void veryLongInput() {
        String longText = "a1".repeat(5_000);
        assertThat(process(longText, DstType.DEFAULT)).hasSize(longText.length());
    }

    @Test
    @DisplayName("[负] null 值原样返回 null（BUG-12 修复后行为：不脱敏不抛错）")
    void nullValueReturnsAsIs() {
        assertThat(process(null, DstType.NAME))
                .as("null 值必须原样返回 null，不抛 NPE")
                .isNull();
        assertThat(DesUtil.processBasic(null, null))
                .as("value 与 context 双 null 同样原样返回")
                .isNull();
    }

    @Test
    @DisplayName("[负] dstContext 为 null 时原样返回（BUG-12 修复后行为）")
    void nullContextReturnsValue() {
        assertThat(DesUtil.processBasic("张三", null))
                .as("dstContext 为 null 时原样返回，不抛 NPE")
                .isEqualTo("张三");
    }

    @Test
    @DisplayName("[负] type 为 null 时原样返回（BUG-12 修复后行为）")
    void nullTypeReturnsValue() {
        assertThat(DesUtil.processBasic("张三", DstContext.builder().build()))
                .as("DstContext.type 为 null 时原样返回，不抛 NPE")
                .isEqualTo("张三");
    }

    @ParameterizedTest(name = "[边界] 中性数字串在 {0} 下处理不崩溃")
    @ValueSource(strings = {"0", "9", "12345678901234567890"})
    void numericStringsSafe(String value) {
        for (DstType type : new DstType[]{DstType.MOBILE, DstType.ID_CARD, DstType.BANK_CARD, DstType.DEFAULT}) {
            String masked = process(value, type);
            assertThat(masked).as("%s 在 %s 下不崩溃", value, type).isNotNull();
        }
    }
}
