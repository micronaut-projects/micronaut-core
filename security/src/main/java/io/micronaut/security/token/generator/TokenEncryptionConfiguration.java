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
package io.micronaut.security.token.generator;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface TokenEncryptionConfiguration {

   /**
    * @return true if an encrypted JWT should be used
    */
    boolean isEnabled();

    /**
     * Full path to the public key so that {@code new File(getPublicKeyPath()).exists() == true}
     */
    String getPublicKeyPath();

    /**
     * Full path to the private key so that {@code new File(getPrivateKeyPath()).exists() == true}
     */
    String getPrivateKeyPath();

    /**
     *
     * @return Any of RSA1_5, RSA-OAEP, RSA-OAEP-256, A128KW, A192KW, A256KW, dir, ECDH-ES, ECDH-ES+A128KW, ECDH-ES+A192KW, ECDH-ES+A256KW, A128GCMKW, A192GCMKW, A256GCMKW, PBES2-HS256+A128KW, PBES2-HS384+A192KW, PBES2-HS512+A256KW
     */
    String getJweAlgorithm();

    /**
     * @return Any of A128CBC-HS256, A192CBC-HS384, A256CBC-HS512, A128CBC+HS256, A256CBC+HS512, A128GCM, A192GCM, A256GCM
     */
    String getEncryptionMethod();
}
