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
package io.micronaut.security.token.jwt.endpoints

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.UsernamePasswordCredentials
import io.micronaut.security.token.jwt.encryption.EncryptionConfiguration
import io.micronaut.security.token.jwt.generator.claims.JwtClaims
import io.micronaut.security.token.jwt.render.AccessRefreshToken
import io.micronaut.security.token.jwt.render.BearerAccessRefreshToken
import io.micronaut.security.token.jwt.signature.SignatureConfiguration
import io.micronaut.security.token.jwt.validator.JwtTokenValidator
import io.micronaut.security.token.validator.TokenValidator
import io.reactivex.Flowable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class OauthControllerSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            [
                    'spec.name': 'endpoints',
                    'micronaut.security.enabled': true,
                    'micronaut.security.endpoints.login.enabled': true,
                    'micronaut.security.endpoints.oauth.enabled': true,
                    'micronaut.security.token.jwt.enabled': true,
                    'micronaut.security.token.jwt.signatures.secret.generator.secret': 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa'
            ], 'test')

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

    def "can obtain a new access token using the refresh token"() {
        expect:
        context.getBean(SignatureConfiguration.class)
        context.getBean(SignatureConfiguration.class, Qualifiers.byName("generator"))

        when:
        context.getBean(EncryptionConfiguration.class)

        then:
        thrown(NoSuchBeanException)

        when:
        def creds = new UsernamePasswordCredentials('user', 'password')
        HttpResponse rsp = client.toBlocking().exchange(HttpRequest.POST('/login', creds), BearerAccessRefreshToken)

        then:
        rsp.status() == HttpStatus.OK
        rsp.body().accessToken
        rsp.body().refreshToken

        when:
        sleep(1_000) // Sleep for one second to give time for Claims issue date to be different
        final String originalAccessToken = rsp.body().accessToken
        final String refreshToken = rsp.body().refreshToken
        def tokenRefreshReq = new TokenRefreshRequest("refresh_token", refreshToken)
        HttpResponse refreshRsp = client.toBlocking().exchange(HttpRequest.POST('/oauth/access_token', tokenRefreshReq), AccessRefreshToken)

        then:
        refreshRsp.status() == HttpStatus.OK
        refreshRsp.body().accessToken
        and:
        refreshRsp.body().accessToken != originalAccessToken

        when:
        TokenValidator tokenValidator = context.getBean(JwtTokenValidator.class)
        Map<String, Object> newAccessTokenClaims = Flowable.fromPublisher(tokenValidator.validateToken(refreshRsp.body().accessToken)).blockingFirst().getAttributes()
        Map<String, Object> originalAccessTokenClaims = Flowable.fromPublisher(tokenValidator.validateToken(originalAccessToken)).blockingFirst().getAttributes()
        List<String> expectedClaims = [JwtClaims.SUBJECT,
                                       JwtClaims.ISSUED_AT,
                                       JwtClaims.EXPIRATION_TIME,
                                       JwtClaims.NOT_BEFORE,
                                       "roles"]
        then:
        expectedClaims.each { String claimName ->
            assert newAccessTokenClaims.containsKey(claimName)
            assert originalAccessTokenClaims.containsKey(claimName)
        }
        originalAccessTokenClaims.get(JwtClaims.SUBJECT) == newAccessTokenClaims.get(JwtClaims.SUBJECT)
        originalAccessTokenClaims.get("roles") == newAccessTokenClaims.get("roles")
        originalAccessTokenClaims.get(JwtClaims.ISSUED_AT) != newAccessTokenClaims.get(JwtClaims.ISSUED_AT)
        originalAccessTokenClaims.get(JwtClaims.EXPIRATION_TIME) != newAccessTokenClaims.get(JwtClaims.EXPIRATION_TIME)
        originalAccessTokenClaims.get(JwtClaims.NOT_BEFORE) != newAccessTokenClaims.get(JwtClaims.NOT_BEFORE)
    }

    def "verify validateTokenRefreshRequest"() {
        given:
        OauthController oauthController = new OauthController(null, null)

        expect:
        !oauthController.validateTokenRefreshRequest(new TokenRefreshRequest(grantType: null, refreshToken: "XXXX"))

        and:
        !oauthController.validateTokenRefreshRequest(new TokenRefreshRequest(grantType: 'foo', refreshToken: "XXXX"))

        and:
        !oauthController.validateTokenRefreshRequest(new TokenRefreshRequest(grantType: 'refresh_token', refreshToken: null))

        and:
        oauthController.validateTokenRefreshRequest(new TokenRefreshRequest(grantType: 'refresh_token', refreshToken: "XXXX"))
    }
}
