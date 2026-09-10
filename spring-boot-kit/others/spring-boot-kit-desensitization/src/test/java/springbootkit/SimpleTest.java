package springbootkit;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.symmetric.AES;
import lombok.extern.slf4j.Slf4j;
import springbootkit.core.util.JsonUtil;
import springbootkit.core.util.SecurityUtil;
import springbootkit.core.util.XmlUtil;
import springbootkit.desensitization.consts.DstType;
import springbootkit.desensitization.dto.DstContext;
import springbootkit.desensitization.util.DesUtil;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Description TODO
 * @Date 2024/11/15
 * @Version 0.0.1
 */
@Slf4j
public class SimpleTest {

    /** 测试用 SM4 密钥（16 字节演示值；库级内置密钥已移除，显式 key 重载免配置） */
    private static final String TEST_SM4_KEY = "0123456789abcdef";

    public static void main(String[] args) {
        try {

            testPattern();
//            testExtractJson();
//            testSM4Cbc();
//            testAesCbc();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void testPattern(){
        String s = DesUtil.processBasic("卓资银行股份有限公司巴音锡勒支行", DstContext.builder().type(DstType.BANK_NAME).build());
        System.out.println(s);
        s = DesUtil.processBasic("安新县农村信用联社股份有限公司大张庄信用社", DstContext.builder().type(DstType.BANK_NAME).build());
        System.out.println(s);
        s = DesUtil.processBasic("中山市财政局", DstContext.builder().type(DstType.BANK_NAME).build());
        System.out.println(s);
//        s = DesUtil.processBasic("麻章区瑞云路sdflj11号", DstContext.builder().type(DstType.ADDRESS).build());
//        System.out.println(s);
//        s = DesUtil.processBasic("麻章县瑞云路sdflj11号", DstContext.builder().type(DstType.ADDRESS).build());
//        System.out.println(s);
//        s = DesUtil.processBasic("麻章xx瑞云路sdflj11号", DstContext.builder().type(DstType.ADDRESS).build());
//        System.out.println(s);

    }
    public static void testExtractJson(){
        String data = json;
        if (JsonUtil.isTypeJSON(data)){
            data = escapeJson(data);
            data = processForStringField(data);
            data = processForArrayField(data);
            data = unescapeJson(data);
        }
        log.info("json:{}", data);
    }
    public static String processForArrayField(String data) {
        String format = "\"%s\"%s[%s\"%s\"%s]";
        Map<String,String> mask = new HashMap<>();
        //match for {"field1": false,"field2": 234.567}
//        String patternString = "([\\s{},\\[\\]]*?)" +Pattern.quote("\"") + "(.*?)" + Pattern.quote("\"") + "(\\s*:+\\s*?)"  + "(\\[\\s*\"+)" +"(.*?)" + "(\"+\\s*]+)"+"([\\s\\S]*?)";
        String patternString = Pattern.quote("\"") + "(.*?)" + Pattern.quote("\"") + "(\\s*:+\\s*?)"  + Pattern.quote("[") + "([\\sa-zA-Z,0-9]*?)" +Pattern.quote("\"")+"(.*?)" +Pattern.quote("\"")+ "(\\s*)" + Pattern.quote("]");
        Pattern pattern = Pattern.compile(patternString, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(data);
        while (matcher.find()) {
            String fieldName = matcher.group(1).substring(matcher.group(1).lastIndexOf("\"")+1);
            String value = matcher.group(4);
            String val = processArrayString(fieldName,value);

            mask.put(matcher.group(0), String.format(format, matcher.group(1),matcher.group(2),matcher.group(3),val,matcher.group(5)));
        }
        for(Map.Entry<String,String> entry : mask.entrySet()){
            data = data.replace(entry.getKey(), entry.getValue());
        }
        return data;
    }

    private static String processArrayString(String fieldName,String val) {
        val = "\"" + val + "\"";
        StrBuilder sb = new StrBuilder();
//        StrBuilder temp = new StrBuilder();
        boolean isBegin = false;
        while (val.contains("\"")){
            int quoteIdx = val.indexOf("\"");
            if(val.trim().indexOf("\"")==0 ){
                sb.append(val.substring(0,quoteIdx+1));
                isBegin = true;
            } else {
                if(isBegin){
                    String value = unescapeJson(val.substring(0, quoteIdx));
                    String enc = "@DST("+SecurityUtil.sm4cbcEncrypt("[LV0]" + value, TEST_SM4_KEY)+")";
                    sb.append(enc);
                    sb.append(val.substring(quoteIdx,quoteIdx+1));
                    isBegin = false;
                } else {
                    sb.append(val.substring(0,quoteIdx+1));
                    isBegin = true;
                }
            }
            val = val.substring(quoteIdx+1);

//                if(isBegin && val.charAt(quoteIdx-1) != '\\'){
//                temp.append(val.substring(0,quoteIdx));
//                String enc = "@DST("+SecurityUtil.sm4cbcEncrypt("[LV0]" + temp)+")";
//                sb.append(enc);
//                sb.append(val.substring(quoteIdx,quoteIdx+1));
//                log.info("process dst [{}] [{}] [{}]",fieldName,temp,enc);
//                temp.reset();
//                isBegin = false;
//
//            } else {
//                if(!isBegin){
//                    sb.append(val.substring(0,quoteIdx +1));
//                    isBegin =true;
//                } else {
//                    temp.append(val.substring(0,quoteIdx+1));
//                }
//            }
//            val = val.substring(quoteIdx+1);
//
//
//            if(val.trim().indexOf(",")==0){
//                sb.append(val.substring(0,val.indexOf(",")+1));
//                val = val.substring(val.indexOf(",")+1);
//            }
        }
        return sb.subString(1,sb.length()-1);
    }

    public static String processForStringField(String data) {
        Map<String,String> mask = new HashMap<>();

        String format = "\"%s\"%s:%s\"%s\"";
        //match for "abc":"dfg"
        String patternString = Pattern.quote("\"") + "(.*?)" + Pattern.quote("\"") +"(\\s*?)"+ Pattern.quote(":") + "(\\s*?)"  + Pattern.quote("\"") +"(.*?)"  + Pattern.quote("\"");
        Pattern pattern = Pattern.compile(patternString, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(data);
        while (matcher.find()) {
            String group = matcher.group(0);
            String fieldName = matcher.group(1).substring(matcher.group(1).lastIndexOf("\"")+1);
            String value = unescapeJson(matcher.group(4));
            String enc = "@DST(" + SecurityUtil.sm4cbcEncrypt("[LV0]" + value, TEST_SM4_KEY) + ")";
            mask.put(group,String.format(format,matcher.group(1),matcher.group(2),matcher.group(3),enc));
        }

        for(Map.Entry<String,String> entry : mask.entrySet()){
            data = data.replace(entry.getKey(), entry.getValue());
        }
        return data;
    }
    private static String escapeJson(String json){
        for(Map.Entry<String,String> entry :  DesUtil.JSON_PLACEHOLDER.entrySet()){
            json = json.replace(entry.getValue(),entry.getKey());
        }
        return json;
    }
    private static  String unescapeJson(String json){
        for(Map.Entry<String,String> entry :  DesUtil.JSON_PLACEHOLDER.entrySet()){
            json = json.replace(entry.getKey(),entry.getValue());
        }
        return json;
    }

    public static void testExtractXml(){
        String data = xml;
        if(XmlUtil.isXml(data)) {
            int cursor = 0;
            Map<String,String> mask = new HashMap<>();
            StrBuilder sb = new StrBuilder();
            String patternString = "(\\s*?)" +Pattern.quote("<") + "(.*?)" + Pattern.quote(">") + "([^<]*?)" + Pattern.quote("</") + "(.*?)" + Pattern.quote(">");
            Pattern pattern = Pattern.compile(patternString, Pattern.DOTALL);
            Matcher matcher = pattern.matcher(data);
            while (matcher.find()) {
                int srcIndex = 0;
                int fieldNameIndex = 2;
                int valIndex = 3;
                String group = matcher.group(srcIndex);
                String temp = group.substring(srcIndex,group.lastIndexOf("</"));
                int tag_end = temp.lastIndexOf("<") + temp.substring(temp.lastIndexOf("<")).indexOf(">") +1;
                String prefix = temp.substring(srcIndex,tag_end);
                sb.append(prefix);
                if(StrUtil.isNotBlank(matcher.group(valIndex))){
                    String fieldName = matcher.group(fieldNameIndex);
                    if (fieldName.contains("<")){
                        fieldName = fieldName.substring(fieldName.lastIndexOf("<")+1);
                    }
                    String encVal = SecurityUtil.sm4cbcEncrypt(matcher.group(valIndex), TEST_SM4_KEY);//DesUtil.desByProperties(fieldName,matcher.group(valIndex));
                    System.out.println(fieldName + ":" + encVal + ":" + matcher.group(valIndex));
                    mask.put(encVal,matcher.group(valIndex));
                    sb.append(encVal);
                } else {
                    sb.append(matcher.group(valIndex));
                }
                sb.append(group.substring(group.lastIndexOf("</")));
                cursor += group.length();
            }
            data = sb + data.substring(cursor);
        }
        System.out.printf(data);
    }

    public static void testAesCbc() throws NoSuchAlgorithmException {
        String key = "0123456789abcdef0123456789abcdef"; // 32 字节演示 AES-256 key
        byte[] vi = SecureRandom.getInstanceStrong().generateSeed(16);
        System.out.printf(Arrays.toString(vi));
        AES aes = new AES(Mode.CBC, Padding.PKCS5Padding, key.getBytes(),vi);
        byte[] encByte = aes.encrypt("abc");
        String enc = Base64.encode(ArrayUtil.addAll(vi, encByte));
        System.out.println(enc);

        byte[] decode = Base64.decode(enc);
        byte[] viRes = ArrayUtil.sub(decode, 0, 16);
        byte[] contentRes = ArrayUtil.sub(decode, 16, decode.length);

        String dec = aes.decryptStr(contentRes);
        System.out.printf(dec);
    }
    public static void testSM4Cbc() throws NoSuchAlgorithmException {
        String s = SecurityUtil.sm4cbcEncrypt("abc", TEST_SM4_KEY);
        String s1 = SecurityUtil.sm4cbcDecrypt(s, TEST_SM4_KEY);
        System.out.printf(s1);
    }


    private static final String json2 = "[\"abc\":123,\"efg\":654,\"kkk\":45455]";
    private static final String json = "{\n" +
            "\t\"body\": {\n" +
            "\t\t\"brands\": [\"ab\\\",\\\"c\"   ,\r\n12306,\"kkk\"],\n" +
            "\t\t\"brands3\": [],\n" +
            "\t\t\"brands4\": [\"\"],\n" +
            "\t\t\"brands2\": [[\"abc\",\"efg\",\"kkk\"],[\"abc\",\"efg\",\"kkk\"]],\n" +
            "\t\t\"brands5\": { \"subField1\": [12306, 12306,\"kkk\"],\"subField1\": \"testdfsfsd\"" +
            "},\n" +
            "\t\t\"amount\": 69.0,\n" +
            "\t\t\"applicantInfo\": {\n" +
            "\t\t\t\"birthDay\": \"1990-01-01\",\n" +
            "\t\t\t\"idNumber\": \"110101190001011234\",\n" +
            "\t\t\t\"idType\": \"01\",\n" +
            "\t\t\t\"mobile\": \"13800138000\",\n" +
            "\t\t\t\"name\": \"张\\\",\\\"三\\\",\\\"丰\\\"\"\n" +
            "\t\t\t\"name2\": \"\"\n" +
            "\t\t},\n" +
            "\t\t\"businessUnitInfo\": {\n" +
            "\t\t\t\"businessUnitCode\": \"BUC110000DM1_01\"\n" +
            "\t\t},\n" +
            "\t\t\"dataPolicySigFlag\": false,\n" +
            "\t\t\"dataSignFlag\": false,\n" +
            "\t\t\"inexpireTime\": \"2024-11-20 10:42:54\",\n" +
            "\t\t\"insuredInfos\": [\n" +
            "\t\t\t{\n" +
            "\t\t\t\t\"birthDay\": \"1990-01-01\",\n" +
            "\t\t\t\t\"idNumber\": \"110101190001011234\",\n" +
            "\t\t\t\t\"idType\": \"01\",\n" +
            "\t\t\t\t\"insureOrderNo\": \"DEMO-ORDER-0001\",\n" +
            "\t\t\t\t\"insuredIdentity\": \"0\",\n" +
            "\t\t\t\t\"mobile\": \"13800138000\",\n" +
            "\t\t\t\t\"name\": \"张三丰\"\n" +
            "\t\t\t}\n" +
            "\t\t],\n" +
            "\t\t\"paySuccessNoticUrl\": \"https://pay.example.com/demo/payNotify\",\n" +
            "\t\t\"payType\": \"C\",\n" +
            "\t\t\"policyInfo\": {\n" +
            "\t\t\t\"endDate\": \"2025-12-31\",\n" +
            "\t\t\t\"endDateStr\": \"2025-12-31\",\n" +
            "\t\t\t\"endHour\": 24,\n" +
            "\t\t\t\"insureCount\": 1,\n" +
            "\t\t\t\"loanMonth\": 0,\n" +
            "\t\t\t\"loanYear\": 0,\n" +
            "\t\t\t\"operateTime\": \"2024-11-20 10:40:54.659\",\n" +
            "\t\t\t\"policyCount\": 0,\n" +
            "\t\t\t\"startDate\": \"2025-01-01\",\n" +
            "\t\t\t\"startDateStr\": \"2025-01-01\",\n" +
            "\t\t\t\"startHour\": 0,\n" +
            "\t\t\t\"sumAmount\": 3000000,\n" +
            "\t\t\t\"sumPremium\": 69.00,\n" +
            "\t\t\t\"unit\": 1\n" +
            "\t\t},\n" +
            "\t\t\"productInfo\": {\n" +
            "\t\t\t\"amount\": 3000000,\n" +
            "\t\t\t\"premium\": 69.00,\n" +
            "\t\t\t\"rationType\": \"WDF11000n7_DM1\",\n" +
            "\t\t\t\"riskCode\": \"WDF\"\n" +
            "\t\t},\n" +
            "\t\t\"returnUrl\": \"\",\n" +
            "\t\t\"signinginfo\": {\n" +
            "\t\t\t\"estimateddate\": \"2025-12-01\",\n" +
            "\t\t\t\"iscontract\": \"1\",\n" +
            "\t\t\t\"multiaccountsouterid\": \"张*丰(DEMO-ORDER-0001)\",\n" +
            "\t\t\t\"openid\": \"oDEMO0000000000000000000000X\",\n" +
            "\t\t\t\"oppactname\": \"张三丰\",\n" +
            "\t\t\t\"signInsureOrderNo\": \"SIGN_ALL\"\n" +
            "\t\t}\n" +
            "\t},\n" +
            "\t\"data\": \"+YTTZSOpGABGR7jBnsCOnJikmmP5NUfLlEmP3abG9yksJy+MPz+Z3jl2/39Vjp0jDFWec6I8n7VvPsVj+bKzy1FGcMXCgM5+1+y3QX51WHzPyj2WVxCwqjuVxPmxPjrbS0Ha+y87SODAajD8MGo/PJcPd1ANHJPhyVN1fVdIzlMGnhyP89FF/RTcywqheK6jlVPffd8Dy6YWSAK9Tv3l2ZHjZTN0iWayzLQ6NuTsKnBnH2vIhcW5oIjv3WKQcFd0zB/NHFIE4e72PvNm8Iqp6910Vj9Kn8kAlcSooulVSYbr1gTPtUT7gavEvJbXdeSL3G8tNZfoqr4Le4PmZLRmqwV+nMXZG1BZwVLhRyu830rPP64W33RG1cMGJ9n2AAJF8ku/gDSu0bMXtD9U2KT5lfLL5yAB/59vAnYqbSHB8pLIL34y4xLNEeuGfYXU9pMLmK9tWGk4kunV7WW9EG76qjUJg/JxYvtnNUW2iNNr89g8zSo5E4/ERa+42Vd6Kl632jQNuEiBDsFwcGscbmtI3ZlYoLE1hwroZfWeht9cOg4QJNT60Fi+ywnwdccPtG0EvrItIMfAXrpnaGF1YT00bX8noCSBO65gYP7T4qaHH+75paCuI8OpvPQ1cMIp6MNxdyatbIm9y27E2DZFWdqfaTJFsMDd0lcukHmywv/fvEjd0Gx4VQm9FkladkuDBpPTsmOFUj1OjjwJ18F+86smUGHrxHwbllelsO0/UT2HXALAgrg9yQwdVbs8D0+68CRG1RDI0uPZ7l2yMwAiHB1/Onc3lGCq+7dnMNCeoRDH2gH3YV/BMh/73U20GOTgQT/h02uTSOyAL2erPqKct88+ggWQCChcjzOmBASvMFBMsubgNXYSaWlRSEKTZamgezXEfnOBW+CSln9KtM9YLb7JtrQd2vdZseixCUM0rXBkKgS05+dJXYwZkvqhT2P20ZWHzZfwdA8qgjoPDkF4n1S0ixEymp5MB7NIBFNXGYu8QtgIeLXuQoZQmLriK2hBN4fCWVqwpnr3/mnfBhbVumceCDV36tBPFc7xBUnwrTZ4mwLiYQ+Sr9ZICpaZv8iqhaN2ygOllB1HFBWjw9yjKJGwF3kEoFVTDAUlPzmrLtoyWsRTooHc4IpIIh2n6OOGv3rA1Oit2rS2YhcCZpvxnN3eMFYSROHVXs47EuUAPvFlubngtjeMRnaaobKBA3idngFvpWVxtNY5NAu8hCOQtYsYZ5hEagfquH1g5Lmvx3zZLsDWF6nv1bPGV3iOeiojHZDkCRksVjL4V9vIUjVUhctXeQKPe+CJhuQPLi/tZjF0nlQUEqhYiJYnBbAfyhM9DJO/WfWN2VpnmiwBHNMbO+V5h7M/zkFlYMVw3qttMNw6G8aR44PeAfcdFB654Oo5CX+sZN/xMknutIopY2D0\",\n" +
            "\t\"requestHead\": {\n" +
            "\t\t\"md5EncryptedValue\": \"642fade73bbd390ff588fea8bc30c563\",\n" +
            "\t\t\"pipeCode\": \"EOP110000DM1\",\n" +
            "\t\t\"requestUUID\": \"c0f0c4d9628f49ce8a6241887bdc2427\"\n" +
            "\t},\n" +
            "\t\"sign\": \"+YTTZSOpGABGR6zOid6SnNX2h2ulE0jGknmRz6yAmzNiMCPccXXri3IypDkBgJl1QwCfZ6Fr26x1b8dI7eezzRBUKdLAh9xjxarnARo0Ci2ZiH7oCjz75UyOzI/ybDeNBROZ8CI4G/THZTu+LyJnZsVUYQJUQ46ikR5rPh8L0EYOiRPGtEWtD4ByGpZ606OvwAHccYMaxLorVCO6QfCOm9X8JnBb0nSined2ELWzXWxrH13Vj/Xp9oiXqgPZU9QWpf7OtSi4UJapkXpZkw==\"\n" +
            "}\n";

    private static final String xml = "<UploadMedResult_Request>\r\n" +
            " <head>\n" +
            " <StandardVersionCode>JKXZX V2.0.0.0</StandardVersionCode>\n" +
            " <MaxRecord>11</MaxRecord>\n" +
            " <TransRefGUID>DEMO-TRANSREFGUID-000001</TransRefGUID>\n" +
            " </head>\n" +
            " <body>\n" +
            " <StaffInformation>\n" +
            " <ID>110101190001011234</ID>\n" +
            " <HHouseholdName></HHouseholdName>\n" +
            " <Name>张**三</Name> \n" +
            " <FamilyRelations>1</FamilyRelations>\n" +
            " </StaffInformation>\n" +
            " <!--abc-->\n" +
            " <PayeeInformation>\n" +
            " <CustomerType>03</CustomerType>\n" +
            " <CustomerNature>2</CustomerNature>\n" +
            " <BankBranchName>DEMO银行</BankBranchName> \n" +
            " <BankAccountName>DEMO保险（DEMO公司</BankAccountName>\n" +
            " <BankAccountNameAgain>DEMO保险（DEMO公司</BankAccountNameAgain> \n" +
            "  <BankAccount>9999999999</BankAccount>\n" +
            " <BankAccountAgain>9999999999</BankAccountAgain>\n" +
            " <IDType></IDType>\n" +
            " <IDNumber></IDNumber>\n" +
            " <Telephone></Telephone>\n" +
            " <PersonalID>110101190001011234</PersonalID>\n" +
            " <BankNote>城乡居民大xx</BankNote>\n" +
            " <FeeField1></FeeField1>\n" +
            " <FeeField2></FeeField2>\n" +
            " </PayeeInformation>\n" +
            " <MedResultList>\n" +
            " <MedResult>\n" +
            " <Batch>202410251305008|DEMO-CJBBH-00000000001</Batch>\n" +
            " <TransRefGUID>DEMO-TRANSREFGUID-000001</TransRefGUID>\n" +
            " <HospitalCode>DEMO-HOSPITAL-001</HospitalCode>\n" +
            " <ClinicSerialNum>DEMO-TRANSREFGUID-000001</ClinicSerialNum>\n" +
            " <SenderCode>110000</SenderCode>\n" +
            " <ReceiptNum>DEMO-TRANSREFGUID-000001</ReceiptNum>\n" +
            " <MedicalCategory>14</MedicalCategory>\n" +
            " <InpatientDate>2024-08-04 00:00:00</InpatientDate>\n" +
            " <InpatientDiagnosisDiseaseCode>冠状动脉粥样硬化性心脏病</InpatientDiagnosisDiseaseCode> \n" +
            " <InpatientDiagnosisDiseaseName>冠状动脉粥样硬化性心脏病</InpatientDiagnosisDiseaseName> \n" +
            " <Discharge_date>2024-08-04 00:00:00</Discharge_date>\n" +
            " <Discharge_Reason>1</Discharge_Reason>\n" +
            " <DischargeCheifDiagnosis>冠状动脉粥样硬化性心脏病</DischargeCheifDiagnosis> \n" +
            " <DischargeCheifDiagnosisName>冠状动脉粥样硬化性心脏病</DischargeCheifDiagnosisName> \n" +
            " <DepartmentCode></DepartmentCode>\n" +
            " <DepartmentName>心血管内科门诊</DepartmentName> \n" +
            " <SickbedNum></SickbedNum>\n" +
            " <DoctorCode></DoctorCode>\n" +
            " <DoctorName></DoctorName>\n" +
            " <ClinicNum>20240915083649138</ClinicNum>\n" +
            " <MedicalPaymentTotalAmount>262.95</MedicalPaymentTotalAmount>\n" +
            " <PlanFundBySI>150.00</PlanFundBySI>\n" +
            " <BigDiseaseCompenFee>64.62</BigDiseaseCompenFee>\n" +
            " <FeeField1>230.77</FeeField1>\n" +
            " <FeeField2>32.18</FeeField2>\n" +
            " <Operator>xx</Operator> \n" +
            " <DistrictCode>110000</DistrictCode>\n" +
            " <MedicareOfficeApplyFlag>1</MedicareOfficeApplyFlag>\n" +
            " <BasicBigDiseaseFlag>0</BasicBigDiseaseFlag>\n" +
            " <Clausecode>220022</Clausecode>\n" +
            " <Kindcode>070039</Kindcode>\n" +
            " <ReserveField1>2024-08-04 00:00:00</ReserveField1>\n" +
            " <ReserveField2>32.18</ReserveField2>\n" +
            " <PersonCheckPayInfo>xx</PersonCheckPayInfo> \n" +
            " <DifferentSigns>0</DifferentSigns>\n" +
            " <NCOV>0</NCOV>\n" +
            " <SetlSummaryBatchNo>202410251305008</SetlSummaryBatchNo>\n" +
            " <BusinessTransactionKey>DEMO-CJBBH-00000000001</BusinessTransactionKey>\n" +
            " </MedResult>\n" +
            " </MedResultList>\n" +
            " </body>\n" +
            " </UploadMedResult_Request>\r\n\r\n";
    private static final String xml2 = "<?xml version=\"1.0\" encoding=\"utf-8\"?><soap:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><yb_init xmlns=\"http://tempuri.org/\"><xml_str_in><context><head><pka100>SID121</pka100><pka789>1100002411190000000001</pka789></head><body><reports><pka002>0</pka002><pkb020>202411141315046</pkb020><pae010>DEMO-CJLB-00000000001</pae010><aac003>张一三</aac003><pka004>1402</pka004><aac001>00000000000000000000000001</aac001><aac002>X000000000</aac002><aka130>21</aka130><pkc018>工商银行</pkc018><pka575>6222020000000000000</pka575><pkc247>王六七</pkc247><pkb042>1226.59</pkb042><paf313>张一三</paf313><aae005>13800138000</aae005><pkb077>2024-11-19</pkb077><pkb076>赵八九</pkb076></reports><reports><pka002>1</pka002><pkb020>202411131315033</pkb020><pae010>DEMO-CJLB-00000000002</pae010><aac003>李四五</aac003><pka004>60</pka004><aac001>00000000000000000001</aac001><aac002>110101190001011234</aac002><aka130>14</aka130><pkc018>广发银行</pkc018><pka575>6222020000000000001</pka575><pkc247>李四五</pkc247><pkb042>3178.65</pkb042><paf313>李四五</paf313><aae005>13900139000</aae005><pkb077>2024-11-19</pkb077><pkb076>赵八九</pkb076></reports><reports><pka002>2</pka002><pkb020>202411131315033</pkb020><pae010>DEMO-CJLB-00000000003</pae010><aac003>李四五</aac003><pka004>60</pka004><aac001>00000000000000000001</aac001><aac002>110101190001011234</aac002><aka130>14</aka130><pkc018>广发银行</pkc018><pka575>6222020000000000001</pka575><pkc247>李四五</pkc247><pkb042>301.77</pkb042><paf313>李四五</paf313><aae005>13900139000</aae005><pkb077>2024-11-19</pkb077><pkb076>赵八九</pkb076></reports></body></context></xml_str_in></yb_init></soap:Body></soap:Envelope>";
}
