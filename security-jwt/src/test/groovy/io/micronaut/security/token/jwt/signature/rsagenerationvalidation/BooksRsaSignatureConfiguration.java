package io.micronaut.security.token.jwt.signature.rsagenerationvalidation;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.security.token.jwt.signature.rsa.RSASignatureConfiguration;

import javax.inject.Named;
import javax.inject.Singleton;
import java.security.interfaces.RSAPublicKey;

@Requires(property = "spec.name", value = "rsajwtbooks")
@Named("validation")
public class BooksRsaSignatureConfiguration implements RSASignatureConfiguration {

    private final RSAPublicKey rsaPublicKey;

    public BooksRsaSignatureConfiguration(@Parameter RSAKey rsaJwk) throws JOSEException {
        this.rsaPublicKey = rsaJwk.toRSAPublicKey();
    }

    @Override
    public RSAPublicKey getPublicKey() {
        return this.rsaPublicKey;
    }
}
