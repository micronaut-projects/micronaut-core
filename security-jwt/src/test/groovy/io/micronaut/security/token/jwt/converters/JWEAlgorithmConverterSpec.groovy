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
