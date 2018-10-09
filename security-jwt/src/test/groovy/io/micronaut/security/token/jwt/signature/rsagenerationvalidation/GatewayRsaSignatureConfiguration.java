package io.micronaut.security.token.jwt.signature.rsagenerationvalidation;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.RSAKey;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.security.token.jwt.signature.rsa.RSASignatureGeneratorConfiguration;

import javax.inject.Named;
import javax.inject.Singleton;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Named("generator")
@Requires(property = "spec.name", value = "rsajwtgateway")
@Singleton
class GatewayRsaSignatureConfiguration implements RSASignatureGeneratorConfiguration {

    private final RSAPublicKey rsaPublicKey;
    private final RSAPrivateKey rsaPrivateKey;

    GatewayRsaSignatureConfiguration(@Parameter RSAKey rsaJwk) throws JOSEException {
        this.rsaPublicKey = rsaJwk.toRSAPublicKey();
        this.rsaPrivateKey = rsaJwk.toRSAPrivateKey();
    }

    @Override
    public RSAPublicKey getPublicKey() {
        return this.rsaPublicKey;
    }

    @Override
    public RSAPrivateKey getPrivateKey() {
        return this.rsaPrivateKey;
    }

    @Override
    public JWSAlgorithm getJwsAlgorithm() {
        return JWSAlgorithm.RS512;
    }
}
