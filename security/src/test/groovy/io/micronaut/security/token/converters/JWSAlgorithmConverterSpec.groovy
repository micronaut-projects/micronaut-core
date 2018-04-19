package io.micronaut.security.token.converters

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
