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

package io.micronaut.docs.signandencrypt

import groovy.util.logging.Slf4j
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMException
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter

import java.security.KeyPair
import java.security.Security

//tag::clazz[]
@Slf4j
class KeyPairProvider {
    /**
     *
     * @param pemPath Full path to PEM file.
     * @return returns KeyPair if successfully for PEM files.
     */
    static Optional<KeyPair> keyPair(String pemPath) {
        // Load BouncyCastle as JCA provider
        Security.addProvider(new BouncyCastleProvider())

        // Parse the EC key pair
        PEMParser pemParser
        try {
            pemParser = new PEMParser(new InputStreamReader(new FileInputStream(pemPath)))
            PEMKeyPair pemKeyPair = (PEMKeyPair) pemParser.readObject()

            // Convert to Java (JCA) format
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter()
            KeyPair keyPair = converter.getKeyPair(pemKeyPair)
            pemParser.close()

            return Optional.of(keyPair)

        } catch (FileNotFoundException e) {
            log.warn("file not found: {}", pemPath)

        } catch (PEMException e) {
            log.warn("PEMException {}", e.getMessage())

        } catch (IOException e) {
            log.warn("IOException {}", e.getMessage())
        }
        return Optional.empty()
    }
}
//end::clazz[]