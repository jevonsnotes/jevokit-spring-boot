package springbootkit.test.server.api;

import com.ulisesbocchio.jasyptspringboot.EncryptablePropertyDetector;
import lombok.RequiredArgsConstructor;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springbootkit.core.consts.JasyptConst;

/**
 * @Description TODO
 * @Date 2023/7/5 10:09
 * @Version 0.0.1
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
