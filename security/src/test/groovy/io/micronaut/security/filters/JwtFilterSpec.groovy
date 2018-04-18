/*
 * Copyright 2017 original authors
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
package io.micronaut.security.filters

import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.management.endpoint.EndpointConfiguration
import io.micronaut.security.config.InterceptUrlMapPattern
import io.micronaut.security.config.SecurityConfigType
import io.micronaut.security.config.SecurityConfiguration
import io.micronaut.security.endpoints.SecurityEndpointsConfiguration
import io.micronaut.security.token.generator.TokenConfiguration
import io.micronaut.security.token.reader.TokenReader
import io.micronaut.security.token.validator.TokenValidator
import org.pac4j.core.profile.jwt.JwtClaims
import spock.lang.Specification

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
class JwtFilterSpec extends Specification {

    def "if both login and refresh endpoints are enabled, two whitelisted InterceptUrlMapPattern are returned"() {
        given:
        SecurityEndpointsConfiguration securityEndpointsConfiguration = Stub(SecurityEndpointsConfiguration) {
            isLogin() >> true
            isRefresh() >> true
        }

        when:
        List<InterceptUrlMapPattern> results = JwtFilter.interceptUrlMapPatternsOfSecurityControllers(securityEndpointsConfiguration)

        then:
        results
        results.size() == 2
        results*.pattern.contains("/login".toString())
        results.find { it.pattern == "/login".toString() }.httpMethod == HttpMethod.POST
        results.find { it.pattern == "/login".toString() }.access == [InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED_ANONYMOUSLY]

        results*.pattern.contains("/oauth/access_token".toString())
        results.find { it.pattern == "/oauth/access_token".toString() }.httpMethod == HttpMethod.POST
        results.find { it.pattern == "/oauth/access_token".toString() }.access == [InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED_ANONYMOUSLY]
    }

    def "if only login is enabled, one whitelisted InterceptUrlMapPattern are returned"() {
        given:
        SecurityEndpointsConfiguration securityEndpointsConfiguration = Stub(SecurityEndpointsConfiguration) {
            isLogin() >> true
            isRefresh() >> false
        }

        when:
        List<InterceptUrlMapPattern> results = JwtFilter.interceptUrlMapPatternsOfSecurityControllers(securityEndpointsConfiguration)

        then:
        results
        results.size() == 1

        results*.pattern.contains("/login".toString())
        results.find { it.pattern == "/login".toString() }.httpMethod == HttpMethod.POST
        results.find { it.pattern == "/login".toString() }.access == [InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED_ANONYMOUSLY]
    }

    def "endpoint pattern adds / preffix to endpoint id"() {
        expect:
        '/health' == JwtFilter.endpointPattern(new EndpointConfiguration('health', null))
    }

    def "if any pattern matches TOKEN_IS_AUTHENTICATED_ANONYMOUSLY, OK is returned"() {
        expect:
        HttpStatus.OK == JwtFilter.filterRequest(null,
                [new InterceptUrlMapPattern('/health', [InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED_ANONYMOUSLY], HttpMethod.GET)],
                null,
                null,
                null)
    }

    def "if tokenReader returns Optional.empty, UNAUTHORIZED is returned"() {
        given:
        def req = Stub(HttpRequest) {
            getUri() >> new URI('/health')
            getMethod() >> HttpMethod.GET
        }
        def tokenReader = Stub(TokenReader) {
            findToken(_) >> Optional.empty()
        }

        expect:
        HttpStatus.UNAUTHORIZED == JwtFilter.filterRequest(req,
                [new InterceptUrlMapPattern('/health', [], HttpMethod.GET)],
                tokenReader,
                null,
                null)
    }

    def "if tokenValidator.validateTokenAndGetClaims return null, UNAUTHORIZED is returned"() {
        given:
        def req = Stub(HttpRequest) {
            getUri() >> new URI('/health')
            getMethod() >> HttpMethod.GET
        }
        def tokenReader = Stub(TokenReader) {
            findToken(_) >> Optional.of('XXXX')
        }

        expect:
        HttpStatus.UNAUTHORIZED == JwtFilter.filterRequest(req,
                [new InterceptUrlMapPattern('/health', [], HttpMethod.GET)],
                tokenReader ,
                Mock(TokenValidator),
                null)
    }

    def "if valid token, return OK if pattern contains IS_AUTHENTICATED"() {
        given:
        def req = Stub(HttpRequest) {
            getUri() >> new URI('/health')
            getMethod() >> HttpMethod.GET
        }
        def tokenReader = Stub(TokenReader) {
            findToken(_) >> Optional.of('XXXX')
        }
        def tokenValidator = Stub(TokenValidator) {
            validateTokenAndGetClaims(_) >> ["${JwtClaims.SUBJECT}": 'sherlock']
        }

        expect:
        HttpStatus.OK == JwtFilter.filterRequest(req,
                [new InterceptUrlMapPattern('/health', [InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED], HttpMethod.GET)],
                tokenReader ,
                tokenValidator,
                null)
    }

    def "if valid token, return UNAUTHORIZED if roles claims not present"() {
        given:
        def req = Stub(HttpRequest) {
            getUri() >> new URI('/health')
            getMethod() >> HttpMethod.GET
        }
        def tokenReader = Stub(TokenReader) {
            findToken(_) >> Optional.of('XXXX')
        }
        def tokenValidator = Stub(TokenValidator) {
            validateTokenAndGetClaims(_) >> ["${JwtClaims.SUBJECT}": 'sherlock']
        }
        def tokenConfiguration = Stub(TokenConfiguration) {
            getRolesClaimName() >> 'roles'
        }

        expect:
        HttpStatus.UNAUTHORIZED == JwtFilter.filterRequest(req,
                [new InterceptUrlMapPattern('/health', ['ROLE_USER'], HttpMethod.GET)],
                tokenReader ,
                tokenValidator,
                tokenConfiguration)
    }

    def "if valid token, return UNAUTHORIZED if roles claims is not a list of strings"() {
        given:
        def req = Stub(HttpRequest) {
            getUri() >> new URI('/health')
            getMethod() >> HttpMethod.GET
        }
        def tokenReader = Stub(TokenReader) {
            findToken(_) >> Optional.of('XXXX')
        }

        def tokenValidator = Stub(TokenValidator) {
            validateTokenAndGetClaims(_) >> [roles: [1, 2]]
        }
        def tokenConfiguration = Stub(TokenConfiguration) {
            getRolesClaimName() >> 'roles'
        }

        expect:
        HttpStatus.UNAUTHORIZED == JwtFilter.filterRequest(req,
                [new InterceptUrlMapPattern('/health', ['ROLE_USER'], HttpMethod.GET)],
                tokenReader ,
                tokenValidator,
                tokenConfiguration)
    }

    def "if valid token, return FORBIDDEN if roles do not match"() {
        given:
        def req = Stub(HttpRequest) {
            getUri() >> new URI('/health')
            getMethod() >> HttpMethod.GET
        }
        def tokenReader = Stub(TokenReader) {
            findToken(_) >> Optional.of('XXXX')
        }

        def tokenValidator = Stub(TokenValidator) {
            validateTokenAndGetClaims(_) >> [roles: ['ROLE_ADMIN']]
        }
        def tokenConfiguration = Stub(TokenConfiguration) {
            getRolesClaimName() >> 'roles'
        }

        expect:
        HttpStatus.FORBIDDEN == JwtFilter.filterRequest(req,
                [new InterceptUrlMapPattern('/health', ['ROLE_USER'], HttpMethod.GET)],
                tokenReader ,
                tokenValidator,
                tokenConfiguration)
    }

    def "findAllPatternsForRequest matches exactly by pattern and httpMethod"() {
        given:
        def req = Stub(HttpRequest) {
            getUri() >> new URI('/books')
            getMethod() >> HttpMethod.GET
        }
        List<InterceptUrlMapPattern> endpointsInterceptUrlMappings = []
        SecurityEndpointsConfiguration securityEndpointsConfiguration = Stub(SecurityEndpointsConfiguration) {
            isLogin() >> false
            isRefresh() >> false
        }
        SecurityConfiguration securityConfiguration = Stub(SecurityConfiguration) {
            getSecurityConfigType() >> SecurityConfigType.INTERCEPT_URL_MAP
            getInterceptUrlMap() >> [new InterceptUrlMapPattern('/books', ['ROLE_USER'], HttpMethod.GET)]
        }

        when:
        List<InterceptUrlMapPattern> result = JwtFilter.findAllPatternsForRequest(req,
                endpointsInterceptUrlMappings,
                securityConfiguration,
                securityEndpointsConfiguration)

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
        List<InterceptUrlMapPattern> endpointsInterceptUrlMappings = []
        SecurityEndpointsConfiguration securityEndpointsConfiguration = Stub(SecurityEndpointsConfiguration) {
            isLogin() >> false
            isRefresh() >> false
        }
        SecurityConfiguration securityConfiguration = Stub(SecurityConfiguration) {
            getSecurityConfigType() >> SecurityConfigType.INTERCEPT_URL_MAP
            getInterceptUrlMap() >> [new InterceptUrlMapPattern('/books', ['ROLE_USER'], HttpMethod.POST)]
        }
        when:
        List<InterceptUrlMapPattern> result = JwtFilter.findAllPatternsForRequest(req,
                endpointsInterceptUrlMappings,
                securityConfiguration,
                securityEndpointsConfiguration)

        then:
        !result
    }

    def "if valid token, return OK if roles present in claim match roles present in intercept url map"() {
        given:
        def req = Stub(HttpRequest) {
            getUri() >> new URI('/health')
            getMethod() >> HttpMethod.GET
        }
        def tokenReader = Stub(TokenReader) {
            findToken(_) >> Optional.of('XXXX')
        }

        def tokenValidator = Stub(TokenValidator) {
            validateTokenAndGetClaims(_) >> [roles: ['ROLE_ADMIN', 'ROLE_USER']]
        }
        def tokenConfiguration = Stub(TokenConfiguration) {
            getRolesClaimName() >> 'roles'
        }

        expect:
        HttpStatus.OK == JwtFilter.filterRequest(req,
                [new InterceptUrlMapPattern('/health', ['ROLE_USER'], HttpMethod.GET)],
                tokenReader ,
                tokenValidator,
                tokenConfiguration)
    }

    def "verifies matchesAccess returns true if any of the access levels is present in a InterceptUrlMap list"() {
        expect:
        !JwtFilter.matchesAccess([], ['ROLE_DETECTIVE'])

        and:
        !JwtFilter.matchesAccess([new InterceptUrlMapPattern('/health', ['ROLE_USER', 'ROLE_ADMIN'], HttpMethod.POST)], ['ROLE_DETECTIVE'])

        and:
        JwtFilter.matchesAccess([new InterceptUrlMapPattern('/health', ['ROLE_USER', 'ROLE_ADMIN'], HttpMethod.POST)], ['ROLE_USER'])

        and:
        JwtFilter.matchesAccess([new InterceptUrlMapPattern('/health', ['ROLE_USER', 'ROLE_ADMIN'], HttpMethod.POST)], ['ROLE_USER', 'ROLE_DETECTIVE'])
    }

    def "areRolesListOfStrings returns true if a list is only composed of strings"() {
        expect:
        !JwtFilter.areRolesListOfStrings(null)

        and:
        !JwtFilter.areRolesListOfStrings('')

        and:
        !JwtFilter.areRolesListOfStrings(['1', 2])

        and:
        JwtFilter.areRolesListOfStrings(['1'])
    }

    def "patternsForRequest filters a list of pattersn given a particular request"() {
        given:
        def req = Stub(HttpRequest) {
            getUri() >> new URI('/health')
            getMethod() >> HttpMethod.GET
        }
        expect:
        JwtFilter.patternsForRequest(req, [new InterceptUrlMapPattern('/health', [], HttpMethod.GET)])

        and:
        !JwtFilter.patternsForRequest(req, [new InterceptUrlMapPattern('/health', [], HttpMethod.POST)])

        !JwtFilter.patternsForRequest(req, [new InterceptUrlMapPattern('/foo', [], HttpMethod.GET)])
    }

    def "if only refresh is enabled, one whitelisted InterceptUrlMapPattern are returned"() {
        given:
        SecurityEndpointsConfiguration securityEndpointsConfiguration = Stub(SecurityEndpointsConfiguration) {
            isLogin() >> false
            isRefresh() >> true
        }

        when:
        List<InterceptUrlMapPattern> results = JwtFilter.interceptUrlMapPatternsOfSecurityControllers(securityEndpointsConfiguration)

        then:
        results
        results.size() == 1

        results*.pattern.contains("/oauth/access_token".toString())
        results.find { it.pattern == "/oauth/access_token".toString() }.httpMethod == HttpMethod.POST
        results.find { it.pattern == "/oauth/access_token".toString() }.access == [InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED_ANONYMOUSLY]
    }
}
