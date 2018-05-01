package io.micronaut.security.token.jwt.signature.rsa;

import com.nimbusds.jose.JWSAlgorithm;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;

@Requires(property = "spec.name", value = "signaturersa")
@Singleton
public class PS512RSASignatureConfiguration implements RSASignatureConfiguration {

    protected final String pemPath;
    private RSAPrivateKey rsaPrivateKey;
    private RSAPublicKey rsaPublicKey;

    public PS512RSASignatureConfiguration(@Value("pemPath") String pemPath) {
        this.pemPath = pemPath;
    }

    @PostConstruct
    void initialize() {
        Optional<KeyPair> keyPair = KeyPairProvider.keyPair(pemPath);
        if ( keyPair.isPresent() ) {
            this.rsaPublicKey = (RSAPublicKey) keyPair.get().getPublic();
            this.rsaPrivateKey = (RSAPrivateKey) keyPair.get().getPrivate();
        }
    }
    @Override
    public JWSAlgorithm getJwsAlgorithm() {
        return JWSAlgorithm.RS512;
    }

    @Override
    public RSAPublicKey getPublicKey() {
        return rsaPublicKey;
    }

    @Override
    public RSAPrivateKey getPrivateKey() {
        return rsaPrivateKey;
    }
}
