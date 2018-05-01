package io.micronaut.security.token.jwt.encryption.secret;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;

/**
 * Encapsulates Secret Encryption Configuration.
 * @author Sergio del Amo
 * @since 1.0
 */
public interface SecretEncryptionConfiguration {

    /**
     *
     * @return The secret being used in {@link SecretEncryption}
     */
    String getSecret();

    /**
     * @return The JWE algorithm
     */
    JWEAlgorithm getJweAlgorithm();

    /**
     *
     * @return {@link EncryptionMethod}
     */
    EncryptionMethod getEncryptionMethod();
}
