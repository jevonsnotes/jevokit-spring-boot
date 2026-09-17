package io.github.jevonsnotes.jevokit.core.consts;

/**
 * Jasypt 加密器常量：ENCRYPTOR_NAME 为配置文件加密（ENC(...) 密文）使用的加密器 Bean 名称，
 * 装配层据此创建/查找对应的 StringEncryptor。
 */
public class JasyptConst {
    public static final String ENCRYPTOR_NAME = "jevokitStringEncryptor";
}
