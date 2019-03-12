/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.security.token.jwt.signature.jwks;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.core.util.functional.ThrowingFunction;
import io.micronaut.core.util.functional.ThrowingSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Default implementation of {@link JwkValidator} which uses a JSON Web Signature (JWS) verifier.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
@Singleton
public class DefaultJwkValidator implements JwkValidator {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultJwkValidator.class);

    @Override
    public boolean validate(SignedJWT jwt, JWK jwk) {
        Optional<JWSVerifier> verifier = getVerifier(jwk);
        if (verifier.isPresent()) {
            try {
                return jwt.verify(verifier.get());
            } catch (JOSEException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("JOSEException when verifying jwt", e);
                }
            }
        }
        return false;
    }

    /**
     *
     * @param jwk A JSON Web Key
     * @return JSON Web Signature (JWS) verifier for the given JSON Web Key.
     */
    protected Optional<JWSVerifier> getVerifier(JWK jwk) {
        if (jwk instanceof RSAKey) {
            RSAKey rsaKey = (RSAKey) jwk;
            return getVerifier(rsaKey::toRSAPublicKey, RSASSAVerifier::new);
        } else if (jwk instanceof ECKey) {
            ECKey ecKey = (ECKey) jwk;
            return getVerifier(ecKey::toECPublicKey, ECDSAVerifier::new);
        }
        return Optional.empty();
    }

    private <T, R extends JWSVerifier> Optional<R> getVerifier(ThrowingSupplier<T, JOSEException> supplier, ThrowingFunction<T, R, JOSEException> consumer) {
        T publicKey = null;
        try {
            publicKey = supplier.get();
        } catch (JOSEException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("JOSEException when retrieving public key", e);
            }
        }
        if (publicKey != null) {
            try {
                return Optional.of(consumer.apply(publicKey));
            } catch (JOSEException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("JOSEException when instantiating the verifier", e);
                }
            }
        }

        return Optional.empty();
    }
}
