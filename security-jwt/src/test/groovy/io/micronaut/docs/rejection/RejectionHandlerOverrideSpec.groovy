/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.docs.rejection

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.security.token.jwt.AuthorizationUtils
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class RejectionHandlerOverrideSpec extends Specification implements AuthorizationUtils {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
        [
            'spec.name': 'rejection-handler',
            'micronaut.security.enabled': true,
            'micronaut.security.endpoints.login.enabled': true,
        ], Environment.TEST)

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())


    void "test the rejection handler can be overridden"() {
        when:
        client.toBlocking().exchange("/rejection-handler")

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.header("X-Reason") == "Example Header"
    }


    @Controller("/rejection-handler")
    @Requires(property = "spec.name", value = "rejection-handler")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    static class SecuredResource {

        @Get
        String foo() {
            ""
        }

    }
}
