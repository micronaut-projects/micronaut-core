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

package io.micronaut.security.filters

import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.management.endpoint.EndpointConfiguration
import io.micronaut.security.config.InterceptUrlMapPattern
import io.micronaut.security.config.SecurityConfigType
import io.micronaut.security.config.SecurityConfiguration
import io.micronaut.security.endpoints.SecurityEndpointsConfiguration
import io.micronaut.security.token.configuration.TokenConfiguration
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

    def "if any pattern matches TOKEN_IS_AUTHENTICATED_ANONYMOUSLY, OK is returned"() {
        expect:
        HttpStatus.OK == new JwtFilter(null, null, null, null).filterRequest(null, [new InterceptUrlMapPattern('/health', [InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED_ANONYMOUSLY], HttpMethod.GET)],)
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
        HttpStatus.UNAUTHORIZED == new JwtFilter(null, null,tokenReader, null).filterRequest(req,
                [new InterceptUrlMapPattern('/health', [], HttpMethod.GET)])
    }

    def "if tokenValidator.validateTokenAndGetClaims is not present, UNAUTHORIZED is returned"() {
        given:
        def req = Stub(HttpRequest) {
            getUri() >> new URI('/health')
            getMethod() >> HttpMethod.GET
        }
        def tokenReader = Stub(TokenReader) {
            findToken(_) >> Optional.of('XXXX')
        }
        def tokenValidator = Stub(TokenValidator) {
            validateTokenAndGetClaims('XXXX') >> Optional.empty()
        }

        expect:
        HttpStatus.UNAUTHORIZED == new JwtFilter(null, tokenValidator, tokenReader, null).filterRequest(req,
                [new InterceptUrlMapPattern('/health', [], HttpMethod.GET)])
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
        HttpStatus.OK == new JwtFilter(null, tokenValidator, tokenReader, null).filterRequest(req,
                [new InterceptUrlMapPattern('/health', [InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED], HttpMethod.GET)])
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
        HttpStatus.UNAUTHORIZED == new JwtFilter(tokenConfiguration, tokenValidator, tokenReader, null).filterRequest(req,
                [new InterceptUrlMapPattern('/health', ['ROLE_USER'], HttpMethod.GET)])
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
        HttpStatus.UNAUTHORIZED == new JwtFilter(tokenConfiguration, tokenValidator, tokenReader, null).filterRequest(req,
                [new InterceptUrlMapPattern('/health', ['ROLE_USER'], HttpMethod.GET)])
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
        HttpStatus.FORBIDDEN == new JwtFilter(tokenConfiguration, tokenValidator, tokenReader, null).filterRequest(req,
                [new InterceptUrlMapPattern('/health', ['ROLE_USER'], HttpMethod.GET)])
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
        HttpStatus.OK == new JwtFilter(tokenConfiguration, tokenValidator, tokenReader, null).filterRequest(req,
                [new InterceptUrlMapPattern('/health', ['ROLE_USER'], HttpMethod.GET)])
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

}
