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

import io.micronaut.security.authentication.Authenticator
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.authentication.UsernamePasswordCredentials
import io.micronaut.security.token.generator.AccessRefreshTokenGenerator
import io.micronaut.security.token.generator.TokenConfiguration
import spock.lang.Specification

class LoginControllerSpec extends Specification {

    def "if authenticator returns empty, return empty"() {
        given:
        def accessRefreshTokenGenerator = Mock(AccessRefreshTokenGenerator)
        def tokenConfiguration = Mock(TokenConfiguration)
        def authenticator = Stub(Authenticator) {
            authenticate(_) >> Optional.empty()
        }
        LoginController loginController = new LoginController(accessRefreshTokenGenerator, tokenConfiguration, authenticator)

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        def rsp = loginController.authenticate(creds)

        then:
        !rsp.isPresent()
    }

    def "if authenticator returns user details authenticate, it is returned"() {
        given:
        def accessRefreshTokenGenerator = Mock(AccessRefreshTokenGenerator)
        def tokenConfiguration = Mock(TokenConfiguration)
        def authenticator = Stub(Authenticator) {
            authenticate(_) >> Optional.of(new UserDetails('admin', ['ROLE_USER']))
        }
        LoginController loginController = new LoginController(accessRefreshTokenGenerator, tokenConfiguration, authenticator)

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        def rsp = loginController.authenticate(creds)

        then:
        rsp.isPresent()
        rsp.get().username == 'admin'
        rsp.get().roles == ['ROLE_USER']
    }
}
