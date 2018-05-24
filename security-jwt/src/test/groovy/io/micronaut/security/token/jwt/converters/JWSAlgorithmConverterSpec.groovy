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

import com.nimbusds.jose.JWSAlgorithm
import spock.lang.Specification
import spock.lang.Unroll

class JWSAlgorithmConverterSpec extends Specification {

    def "trying to convert an invalid JWE Algorithm returns optional.empty"() {
        given:
        JWSAlgorithmConverter converter = new JWSAlgorithmConverter()

        when:
        Optional<JWSAlgorithm> algorithm = converter.convert('FOO', JWSAlgorithm.class, null)

        then:
        !algorithm.isPresent()
    }

    def "trying to convert a null JWE Algorithm returns optional.empty"() {
        given:
        JWSAlgorithmConverter converter = new JWSAlgorithmConverter()

        when:
        Optional<JWSAlgorithm> algorithm = converter.convert(null, JWSAlgorithm.class, null)

        then:
        !algorithm.isPresent()
    }

    @Unroll
    def "trying to convert a valid JWE Algorithm ( #value ) returns a none empty optional"(String value) {
        given:
        JWSAlgorithmConverter converter = new JWSAlgorithmConverter()

        when:
        Optional<JWSAlgorithm> algorithm = converter.convert(value, JWSAlgorithm.class, null)

        then:
        algorithm.isPresent()

        where:
        value << ["HS256",
                  "HS384",
                  "HS512",
                  "RS256",
                  "RS384",
                  "RS512",
                  "ES256",
                  "ES384",
                  "ES512",
                  "PS256",
                  "PS384",
                  "PS512"]
    }
}
