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

import com.nimbusds.jose.EncryptionMethod
import spock.lang.Specification
import spock.lang.Unroll

class EncryptionMethodConverterSpec extends Specification {

    def "trying to convert an invalid encryption method returns optional.empty"() {
        given:
        EncryptionMethodConverter converter = new EncryptionMethodConverter()

        when:
        Optional<EncryptionMethod> encryptionMethod = converter.convert('FOO', EncryptionMethod.class, null)

        then:
        !encryptionMethod.isPresent()
    }

    def "trying to convert a null encryption method returns optional.empty"() {
        given:
        EncryptionMethodConverter converter = new EncryptionMethodConverter()

        when:
        Optional<EncryptionMethod> encryptionMethod = converter.convert(null, EncryptionMethod.class, null)

        then:
        !encryptionMethod.isPresent()
    }

    @Unroll
    def "trying to convert a valid encryption method ( #value ) returns a none empty optional"(String value) {
        given:
        EncryptionMethodConverter converter = new EncryptionMethodConverter()

        when:
        Optional<EncryptionMethod> encryptionMethod = converter.convert(value, EncryptionMethod.class, null)

        then:
        encryptionMethod.isPresent()

        where:
        value << ["A128CBC-HS256", "A192CBC-HS384", "A256CBC-HS512", "A128CBC+HS256", "A256CBC+HS512", "A128GCM", "A192GCM", "A256GCM"]
    }
}
