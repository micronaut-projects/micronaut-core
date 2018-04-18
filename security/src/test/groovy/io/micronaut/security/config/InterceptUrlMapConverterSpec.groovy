package io.micronaut.security.config

import io.micronaut.http.HttpMethod
import spock.lang.Shared
import spock.lang.Subject
import spock.lang.Specification

class InterceptUrlMapConverterSpec extends Specification {

    @Subject
    @Shared
    InterceptUrlMapConverter converter = new InterceptUrlMapConverter()

    def "Empty map is converted to empty optional"() {
        expect:
        !converter.convert([:], InterceptUrlMapPattern).isPresent()
    }

    def "Map without access key is converted to empty optional"() {
        expect:
        !converter.convert([pattern: '/health'], InterceptUrlMapPattern).isPresent()
    }

    def "Map with access key not being a list is converted to empty optional"() {
        expect:
        !converter.convert([pattern: '/health', access: 'foo'], InterceptUrlMapPattern).isPresent()
    }

    def "Map with invalid http method is converted to empty optional"() {
        expect:
        !converter.convert([httpMethod: 'FOO', pattern: '/health', access: ['IS_AUTHENTICATED_ANONYMOUSLY']], InterceptUrlMapPattern).isPresent()
    }

    def "http method is optional in map. default is HTTP Method GET"() {
        when:
        Optional<InterceptUrlMapPattern> i = converter.convert([pattern: '/health', access: ['IS_AUTHENTICATED_ANONYMOUSLY']], InterceptUrlMapPattern)

        then:
        i.isPresent()
        i.get().pattern == '/health'
        i.get().access == ['IS_AUTHENTICATED_ANONYMOUSLY']
        i.get().httpMethod == HttpMethod.GET
    }

    def "http method can be specified"() {
        when:
        Optional<InterceptUrlMapPattern> i = converter.convert([httpMethod: 'POST', pattern: '/health', access: ['IS_AUTHENTICATED_ANONYMOUSLY']], InterceptUrlMapPattern)

        then:
        i.isPresent()
        i.get().pattern == '/health'
        i.get().access == ['IS_AUTHENTICATED_ANONYMOUSLY']
        i.get().httpMethod == HttpMethod.POST
    }

    def "http method can be specified in lowercase"() {
        when:
        Optional<InterceptUrlMapPattern> i = converter.convert([httpMethod: 'post', pattern: '/health', access: ['IS_AUTHENTICATED_ANONYMOUSLY']], InterceptUrlMapPattern)

        then:
        i.isPresent()
        i.get().pattern == '/health'
        i.get().access == ['IS_AUTHENTICATED_ANONYMOUSLY']
        i.get().httpMethod == HttpMethod.POST
    }
}
