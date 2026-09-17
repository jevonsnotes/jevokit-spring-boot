package io.github.jevonsnotes.jevokit.jasypt.resolver;

import com.ulisesbocchio.jasyptspringboot.EncryptablePropertyDetector;
import com.ulisesbocchio.jasyptspringboot.EncryptablePropertyResolver;
import com.ulisesbocchio.jasyptspringboot.detector.DefaultPropertyDetector;
import com.ulisesbocchio.jasyptspringboot.exception.DecryptionException;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.exceptions.EncryptionOperationNotPossibleException;
import org.springframework.util.Assert;

/**
 * @Description TODO
 * @Date 2023/7/5 9:11
 * @Version 0.0.1
 */
public class JevokitEncryptPropertyResolver implements EncryptablePropertyResolver {

    private StringEncryptor encryptor;
    private EncryptablePropertyDetector detector;

    public JevokitEncryptPropertyResolver(StringEncryptor encryptor) {
        this(encryptor, new DefaultPropertyDetector());
    }

    public JevokitEncryptPropertyResolver(StringEncryptor encryptor, EncryptablePropertyDetector detector) {
        Assert.notNull(encryptor, "String encryptor can't be null");
        Assert.notNull(detector, "Encryptable Property detector can't be null");
        this.encryptor = encryptor;
        this.detector = detector;
    }

    @Override
    public String resolvePropertyValue(String value) {
        String actualValue = value;
        if (detector.isEncrypted(value)) {
            try {
                actualValue = encryptor.decrypt(detector.unwrapEncryptedValue(value.trim()));
            } catch (EncryptionOperationNotPossibleException e) {
                throw new DecryptionException("Decryption of Properties failed,  make sure encryption/decryption " +
                        "passwords match", e);
            }
        }
        return actualValue;
    }
}
