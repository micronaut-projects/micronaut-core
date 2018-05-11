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
package io.micronaut.security.token.jwt.converters

import com.nimbusds.jose.JWEAlgorithm
import spock.lang.Specification
import spock.lang.Unroll

class JWEAlgorithmConverterSpec extends Specification {


    def "trying to convert an invalid JWE Algorithm returns optional.empty"() {
        given:
        JWEAlgorithmConverter converter = new JWEAlgorithmConverter()

        when:
        Optional<JWEAlgorithm> algorithm = converter.convert('FOO', JWEAlgorithm.class, null)

        then:
        !algorithm.isPresent()
    }

    def "trying to convert a null JWE Algorithm returns optional.empty"() {
        given:
        JWEAlgorithmConverter converter = new JWEAlgorithmConverter()

        when:
        Optional<JWEAlgorithm> algorithm = converter.convert(null, JWEAlgorithm.class, null)

        then:
        !algorithm.isPresent()
    }

    @Unroll
    def "trying to convert a valid JWE Algorithm ( #value ) returns a none empty optional"(String value) {
        given:
        JWEAlgorithmConverter converter = new JWEAlgorithmConverter()

        when:
        Optional<JWEAlgorithm> algorithm = converter.convert(value, JWEAlgorithm.class, null)

        then:
        algorithm.isPresent()

        where:
        value << ["RSA1_5",
                  "RSA-OAEP",
                  "RSA-OAEP-256",
                  "A128KW",
                  "A192KW",
                  "A256KW",
                  "dir",
                  "ECDH-ES",
                  "ECDH-ES+A128KW",
                  "ECDH-ES+A192KW",
                  "ECDH-ES+A256KW",
                  "A128GCMKW",
                  "A192GCMKW",
                  "A256GCMKW",
                  "PBES2-HS256+A128KW",
                  "PBES2-HS384+A192KW",
                  "PBES2-HS512+A256KW"]
    }
}
