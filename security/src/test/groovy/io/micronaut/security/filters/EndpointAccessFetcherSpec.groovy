package io.micronaut.security.filters

import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.security.config.InterceptUrlMapPattern
import io.micronaut.security.config.SecurityConfigType
import io.micronaut.security.config.SecurityConfiguration
import io.micronaut.security.endpoints.SecurityEndpointsConfiguration
import spock.lang.Specification
import spock.lang.Unroll

class EndpointAccessFetcherSpec extends Specification {

    def "findAllPatternsForRequest matches exactly by pattern and httpMethod"() {
        given:

        def req = Stub(HttpRequest) {
            getUri() >> new URI('/books')
            getMethod() >> HttpMethod.GET
        }
        List<EndpointAccessProvider> endpointAccessProviders = []
        SecurityEndpointsConfiguration securityEndpointsConfiguration = Stub(SecurityEndpointsConfiguration) {
            isLogin() >> false
            isRefresh() >> false
        }
        endpointAccessProviders << new SecurityControllersEndpointAccessProvider(securityEndpointsConfiguration)
        SecurityConfiguration securityConfiguration = Stub(SecurityConfiguration) {
            getSecurityConfigType() >> SecurityConfigType.INTERCEPT_URL_MAP
            getInterceptUrlMap() >> [new InterceptUrlMapPattern('/books', ['ROLE_USER'], HttpMethod.GET)]
        }
        endpointAccessProviders << new ConfigurationEndpointAccessProvider(securityConfiguration)

        when:
        List<InterceptUrlMapPattern> result = new EndpointAccessFetcher(endpointAccessProviders).findAllPatternsForRequest(req)

        then:
        result
        result.size() == 1
        result[0].access == ['ROLE_USER']
        result[0].httpMethod == HttpMethod.GET
        result[0].pattern == '/books'

    }

    def "findAllPatternsForRequest do not match if httpMethod is not the same"() {
        given:
        def req = Stub(HttpRequest) {
            getUri() >> new URI('/books')
            getMethod() >> HttpMethod.GET
        }
        List<EndpointAccessProvider> endpointAccessProviders = []
        SecurityEndpointsConfiguration securityEndpointsConfiguration = Stub(SecurityEndpointsConfiguration) {
            isLogin() >> false
            isRefresh() >> false
        }
        endpointAccessProviders << new SecurityControllersEndpointAccessProvider(securityEndpointsConfiguration)

        SecurityConfiguration securityConfiguration = Stub(SecurityConfiguration) {
            getSecurityConfigType() >> SecurityConfigType.INTERCEPT_URL_MAP
            getInterceptUrlMap() >> [new InterceptUrlMapPattern('/books', ['ROLE_USER'], HttpMethod.POST)]
        }
        endpointAccessProviders << new ConfigurationEndpointAccessProvider(securityConfiguration)

        when:
        List<InterceptUrlMapPattern> result = new EndpointAccessFetcher(endpointAccessProviders).findAllPatternsForRequest(req)

        then:
        !result
    }

    @Unroll
    def "patternsForRequest filters a list of patterns given a particular request"( String pattern, List<String> access, HttpMethod method, boolean expectation) {
        given:
        def req = Stub(HttpRequest) {
            getUri() >> new URI('/health')
            getMethod() >> HttpMethod.GET
        }
        when:
        List<EndpointAccessProvider> endpointAccessProviders = []
        def endpointAccessProvider = Stub(EndpointAccessProvider) {
            findEndpointAccessRestrictions() >> Optional.of([new InterceptUrlMapPattern(pattern, access, method)])
        }
        endpointAccessProviders << endpointAccessProvider
        boolean result = new EndpointAccessFetcher(endpointAccessProviders).findAllPatternsForRequest(req) as boolean

        then:
        result  == expectation

        where:
        pattern | access | method | expectation
        '/health'| [] |  HttpMethod.GET | true
        '/health' | [] |  HttpMethod.POST | false
        '/foo' | [] |  HttpMethod.GET | false
    }
}

class MockEndpointAccessProvider implements EndpointAccessProvider {
    List<InterceptUrlMapPattern> patterns
    MockEndpointAccessProvider(List<InterceptUrlMapPattern> patterns) {
        this.patterns = patterns
    }

    Optional<List<InterceptUrlMapPattern>> findEndpointAccessRestrictions() {
        Optional.of(patterns)
    }
}
