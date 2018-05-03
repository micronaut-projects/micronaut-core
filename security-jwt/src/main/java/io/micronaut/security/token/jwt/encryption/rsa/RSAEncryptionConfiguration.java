package io.micronaut.security.token.jwt.encryption.rsa;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * @author Sergio del Amo
 * @version 1.0
 */
public interface RSAEncryptionConfiguration {

    /**
     *
     * @return RSA public Key
     */
    RSAPublicKey getPublicKey();

    /**
     *
     * @return RSA private Key
     */
    RSAPrivateKey getPrivateKey();

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
