package io.github.jevonsnotes.jevokit.sample.api;

import com.ulisesbocchio.jasyptspringboot.EncryptablePropertyDetector;
import lombok.RequiredArgsConstructor;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.github.jevonsnotes.jevokit.core.consts.JasyptConst;

/**
 * 配置加解密演示端点（/jasypt/test）：注入 JasyptConst.ENCRYPTOR_NAME 指定的加密器，
 * 演示 ENC 密文与明文的往返加解密（E2E 用它验证 datasource.password 密文可用性）。
 */
@RestController
@RequestMapping("/jasypt")
@RequiredArgsConstructor
public class JasyptApi {
    @Autowired
    @Qualifier(JasyptConst.ENCRYPTOR_NAME)
    private StringEncryptor encryptor;

    private final EncryptablePropertyDetector detector;
    @RequestMapping("/test")
    public String test(@RequestParam("encryptVal") String encryptVal){
        if(detector.isEncrypted(encryptVal)){
            encryptVal = detector.unwrapEncryptedValue(encryptVal);
        }
        return encryptor.decrypt(encryptVal);
    }
}
