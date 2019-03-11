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
package io.micronaut.security.session

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.annotation.Secured
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class UnauthorizedTargetUrlSpec extends Specification {
    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                 : UnauthorizedTargetUrlSpec.simpleName,
            'micronaut.security.enabled': true,
            'micronaut.security.session.enabled': true,
            'micronaut.security.session.unauthorized-target-url': '/login/auth',
            'micronaut.security.intercept-url-map': [
                    [pattern: '/login/auth', httpMethod: 'GET', access: ['isAnonymous()']]
            ]
    ], Environment.TEST)

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "access a secured controller without authentication redirects to micronaut.security.session.unauthorized-target-url"() {
        when:
        HttpRequest request = HttpRequest.GET("/foo/bar")
                .header('Accept', 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8')
                .header('Accept-Language', 'en-us')
                .header('Accept-Encoding', 'gzip, deflate')
                .header('User-Agent', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/12.0 Safari/605.1.15')
        client.toBlocking().exchange(request)

        then:
        noExceptionThrown()
    }

}

@Requires(property = 'spec.name', value = 'UnauthorizedTargetUrlSpec')
@Secured('isAuthenticated()')
@Controller('/foo')
class SecuredController {

    @Get("/bar")
    Map<String, Object> index() {
        [:]
    }
}

@Requires(property = 'spec.name', value = 'UnauthorizedTargetUrlSpec')
@Controller('/login')
class LoginController {

    @Get("/auth")
    Map<String, Object> auth() {
        [:]
    }
}
