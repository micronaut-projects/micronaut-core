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

package io.micronaut.security.endpoints

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.UsernamePasswordCredentials
import io.micronaut.security.token.render.AccessRefreshToken
import io.micronaut.security.token.render.BearerAccessRefreshToken
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class OauthControllerSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(
            [
                    "spec.name": "endpoints",
                    "micronaut.security.enabled": true,
                    "micronaut.security.endpoints.login": true,
                    "micronaut.security.endpoints.refresh": true,
                    "micronaut.security.token.signature.secret": 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa'
            ], 'test')

    @Shared EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
    @Shared @AutoCleanup HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

    def "can obtain a new access token using the refresh token"() {
        when:
        def creds = new UsernamePasswordCredentials('user', 'password')
        HttpResponse rsp = client.toBlocking().exchange(HttpRequest.POST('/login', creds), BearerAccessRefreshToken)

        then:
        rsp.status() == HttpStatus.OK
        rsp.body().accessToken
        rsp.body().refreshToken

        when:
        final String originalAccessToken = rsp.body().accessToken
        final String refreshToken = rsp.body().refreshToken
        def tokenRefreshReq = new TokenRefreshRequest("refresh_token", refreshToken)
        HttpResponse refreshRsp = client.toBlocking().exchange(HttpRequest.POST('/oauth/access_token', tokenRefreshReq), AccessRefreshToken)

        then:
        refreshRsp.status() == HttpStatus.OK
        refreshRsp.body().accessToken
//
//        and:
//        refreshRsp.body().accessToken != originalAccessToken
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
