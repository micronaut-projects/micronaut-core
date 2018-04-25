package io.micronaut.security.jwt.converters

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
