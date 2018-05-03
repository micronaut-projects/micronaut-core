/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.security.token.jwt.encryption;

import com.nimbusds.jose.*;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;

/**
 * Abstract encryption configuration.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public abstract class AbstractEncryptionConfiguration implements EncryptionConfiguration {

    protected JWEAlgorithm algorithm;

    protected EncryptionMethod method;

    @Override
    public String encrypt(final JWT jwt) throws JOSEException, ParseException {

        if (jwt instanceof SignedJWT) {
            // Create JWE object with signed JWT as payload
            final JWEObject jweObject = new JWEObject(
                    new JWEHeader.Builder(this.algorithm, this.method).contentType("JWT").build(),
                    new Payload((SignedJWT) jwt));


            // Perform encryption
            jweObject.encrypt(buildEncrypter());

            // Serialise to JWE compact form
            return jweObject.serialize();
        } else {
            // create header
            final JWEHeader header = new JWEHeader(this.algorithm, this.method);


            // encrypted jwt
            EncryptedJWT encryptedJwt = new EncryptedJWT(header, jwt.getJWTClaimsSet());

            // Perform encryption
            encryptedJwt.encrypt(buildEncrypter());

            // serialize
            return encryptedJwt.serialize();
        }
    }

    /**
     * Build the appropriate encrypter.
     * @throws JOSEException could be thrown while building encrypter if configuration is invalid
     * @return the appropriate encrypter
     */
    protected abstract JWEEncrypter buildEncrypter() throws JOSEException;

    @Override
    public void decrypt(final EncryptedJWT encryptedJWT) throws JOSEException {
        encryptedJWT.decrypt(buildDecrypter());
    }

    /**
     * Build the appropriate decrypter.
     * @throws JOSEException could be thrown while building decrypter if configuration is invalid
     * @return the appropriate decrypter
     */
    protected abstract JWEDecrypter buildDecrypter() throws JOSEException;

    /**
     * algorithm Getter.
     * @return Instance of {@link JWEAlgorithm}
     */
    public JWEAlgorithm getAlgorithm() {
        return algorithm;
    }

    /**
     * algorithm Setter.
     * @param algorithm Instance of {@link JWEAlgorithm}
     */
    public void setAlgorithm(final JWEAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * method Getter.
     * @return Instance of {@link EncryptionMethod}
     */
    public EncryptionMethod getMethod() {
        return method;
    }

    /**
     * method Setter.
     * @param method Instance of {@link EncryptionMethod}
     */
    public void setMethod(final EncryptionMethod method) {
        this.method = method;
    }
}
