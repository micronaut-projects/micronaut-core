package io.micronaut.security.token.jwt.encryption.ec;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;

import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

/**
 * @author Sergio del Amo
 * @since 1.0
 */
public interface ECEncryptionConfiguration {

    /**
     *
     * @return EC Public Key
     */
    ECPublicKey getPublicKey();

    /**
     *
     * @return EC Private Key
     */
    ECPrivateKey getPrivateKey();

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
