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
package io.micronaut.security.token.jwt.bearer

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.UsernamePasswordCredentials
import io.micronaut.security.token.jwt.encryption.EncryptionConfiguration
import io.micronaut.security.token.jwt.render.BearerAccessRefreshToken
import io.micronaut.security.token.jwt.signature.SignatureConfiguration
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class AccessRefreshTokenLoginHandlerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'accessrefershtokenloginhandler',
            'micronaut.security.enabled': true,
            'micronaut.security.endpoints.login.enabled': true,
            'micronaut.security.token.jwt.enabled': true,
            'micronaut.security.token.jwt.signatures.secret.generator.secret': 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa',
            ], "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    @Unroll
    void "test invalid authentication with username #username"() {
        expect:
        embeddedServer.applicationContext.getBean(SignatureConfiguration.class)

        when:
        embeddedServer.applicationContext.getBean(EncryptionConfiguration.class)

        then:
        thrown(NoSuchBeanException)

        when:
        def creds = new UsernamePasswordCredentials(username, password)
        client.toBlocking().exchange(HttpRequest.POST('/login', creds))

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
        e.message == message

        where:
        username          | password  | message
        "notFound"        | "valid"   | "User Not Found"
        "valid"           | "invalid" | "Credentials Do Not Match"
        "disabled"        | "valid"   | "User Disabled"
        "accountExpired"  | "valid"   | "Account Expired"
        "passwordExpired" | "valid"   | "Password Expired"
        "accountLocked"   | "valid"   | "Account Locked"
    }

    void "test valid authentication"() {
        when:
        def creds = new UsernamePasswordCredentials("valid", "valid")
        def resp = client.toBlocking().exchange(HttpRequest.POST('/login', creds), BearerAccessRefreshToken)

        then:
        resp.status == HttpStatus.OK
        resp.body().accessToken
        resp.body().refreshToken
        resp.body().username == "valid"
        resp.body().roles == ["foo", "bar"]
        resp.body().expiresIn
    }
}
