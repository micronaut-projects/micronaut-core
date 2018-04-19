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

package io.micronaut.security.authentication

import spock.lang.Specification

class AuthenticatorSpec extends Specification {

    def "if no authentication providers return empty optional"() {
        given:
        Authenticator authenticator = new Authenticator()

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        Optional<AuthenticationResponse> rsp = authenticator.authenticate(creds)

        then:
        !rsp.isPresent()
    }

    def "if any authentication provider throws exception, continue with authentication"() {
        given:
        def authProviderExceptionRaiser = Stub(AuthenticationProvider) {
            authenticate(_) >> { throw new Exception('Authentication provider raised exception') }
        }
        def authProviderOK = Stub(AuthenticationProvider) {
            authenticate(_) >> new UserDetails('admin', [])
        }
        Authenticator authenticator = new Authenticator([authProviderExceptionRaiser, authProviderOK])

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        Optional<AuthenticationResponse> rsp = authenticator.authenticate(creds)

        then:
        rsp.isPresent()
        rsp.get() instanceof UserDetails
    }

    def "if no authentication provider can authentication, the last error is sent back"() {
        given:
        def authProviderFailed = Stub(AuthenticationProvider) {
            authenticate(_) >> new AuthenticationFailed()
        }
        Authenticator authenticator = new Authenticator([authProviderFailed])

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        Optional<AuthenticationResponse> rsp = authenticator.authenticate(creds)

        then:
        rsp.get() instanceof AuthenticationFailed
    }
}
