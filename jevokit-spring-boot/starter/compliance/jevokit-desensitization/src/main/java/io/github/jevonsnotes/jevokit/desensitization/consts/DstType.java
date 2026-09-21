package io.github.jevonsnotes.jevokit.desensitization.consts;

/**
 * 脱敏数据类型枚举：决定 BASIC 算法下的具体掩码规则（保留哪些明文段、屏蔽多少位）。
 * 使用方式：在 {@code @DstProperty(type=...)} 或 jevokit.desensitization.prop-pattern 规则中指定；
 * 各类型的保留/屏蔽策略见 {@code DesUtil.processBasic}（如 MOBILE 大陆号保留前 3 后 4，PASSWORD 全屏蔽）。
 * <p>关键约束：DEFAULT 为兜底策略（按长度分段保留首尾）；不识别的格式（如 MOBILE 不匹配三地号码规则）
 * 时值原样返回——宁可漏脱敏不抛错，业务侧须自行保证字段类型标注正确。
 */
public enum DstType {
    /**
     * 默认
     */
    DEFAULT,
    /**
     *
     */
    ID_CARD,
    /**
     * 姓名(仅中文)
     */
    NAME,
    /**
     * 移动手机号
     */
    MOBILE,
    /**
     * 固定电话
     */
    PHONE,
    /**
     * 电子邮箱地址
     */
    EMAIL,
    /**
     * 地址
     */
    ADDRESS,
    /**
     * 银行卡号
     */
    BANK_CARD,
    /**
     * 银行名称
     */
    BANK_NAME,
    /**
     * 公司开户银行联号
     */
    BANK_LINE_NUM,
    /**
     * 车牌号
     */
    LICENSE_PLATE,
    /**
     * 密码
     */
    PASSWORD,
    /**
     * 微信
     */
    WECHAT,
    /**
     * 工作单位
     */
    WORK_UNIT,

    CUSTOM,

}
