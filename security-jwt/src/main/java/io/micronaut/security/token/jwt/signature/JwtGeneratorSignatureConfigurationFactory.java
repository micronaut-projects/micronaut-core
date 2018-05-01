package io.micronaut.security.token.jwt.signature;

import com.nimbusds.jose.JWSAlgorithm;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.security.token.jwt.signature.secret.SecretSignature;
import io.micronaut.security.token.jwt.signature.secret.SecretSignatureConfiguration;

import javax.inject.Singleton;

/**
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = JwtGeneratorSignatureConfigurationProperties.PREFIX + ".secret")
@Factory
public class JwtGeneratorSignatureConfigurationFactory {

    private final JwtGeneratorSignatureConfigurationProperties jwtGeneratorSignatureConfigurationProperties;

    /**
     * Constructor.
     * @param jwtGeneratorSignatureConfigurationProperties Generation Signature configuration
     */
    public JwtGeneratorSignatureConfigurationFactory(JwtGeneratorSignatureConfigurationProperties jwtGeneratorSignatureConfigurationProperties) {
        this.jwtGeneratorSignatureConfigurationProperties = jwtGeneratorSignatureConfigurationProperties;
    }

    /**
     * The client returned from a builder.
     * @return client object
     */
    @Primary
    @Singleton
    public SignatureConfiguration jwtGeneratorSignatureConfiguration() {
        return new SecretSignature(new SecretSignatureConfiguration() {
            @Override
            public JWSAlgorithm getJwsAlgorithm() {
                return jwtGeneratorSignatureConfigurationProperties.getJwsAlgorithm();
            }

            @Override
            public String getSecret() {
                return jwtGeneratorSignatureConfigurationProperties.getSecret();
            }
        });
    }
}
