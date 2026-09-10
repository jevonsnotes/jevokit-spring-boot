package springbootkit.jasypt.util;

import com.ulisesbocchio.jasyptspringboot.EncryptablePropertyDetector;
import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;

/**
 * @Description TODO
 * @Date 2023/7/5 14:35
 * @Version 0.0.1
 */
@Slf4j
public class JasyptUtil {
    private static StringEncryptor encryptor;
    private static EncryptablePropertyDetector detector;

    public JasyptUtil(StringEncryptor encryptor,EncryptablePropertyDetector detector){
        JasyptUtil.encryptor = encryptor;
        JasyptUtil.detector = detector;
    }

    public static String encrypt(String val){
        return encryptor.encrypt(val);
    }

    public static String decrypt(String val){
        if(detector.isEncrypted(val)){
            val =  detector.unwrapEncryptedValue(val);
        }
        return encryptor.decrypt(val);
    }

}
