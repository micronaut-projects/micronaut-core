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
package io.micronaut.security.token.jwt.signature.rsagenerationvalidation;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.security.token.jwt.signature.rsa.RSASignatureConfiguration;

import javax.inject.Named;
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
