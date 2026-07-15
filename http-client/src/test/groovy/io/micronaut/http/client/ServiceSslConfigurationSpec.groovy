/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Issue
import spock.lang.Specification

@Issue("https://github.com/micronaut-projects/micronaut-core/issues/12757")
class ServiceSslConfigurationSpec extends Specification {

    void "a per-service ssl block is enabled by default but can still be disabled explicitly"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.http.services.myservice.url'                                : 'https://example.com',
                'micronaut.http.services.myservice.ssl.insecure-trust-all-certificates': true,
                'micronaut.http.services.disabled.url'                                 : 'https://example.com',
                'micronaut.http.services.disabled.ssl.enabled'                         : false,
        ])

        expect: 'declaring a per-service ssl block enables SSL by default (otherwise HTTPS silently breaks)'
        ctx.getBean(ServiceHttpClientConfiguration, Qualifiers.byName('myservice')).sslConfiguration.enabled

        and: 'an explicit ssl.enabled=false still wins'
        !ctx.getBean(ServiceHttpClientConfiguration, Qualifiers.byName('disabled')).sslConfiguration.enabled

        cleanup:
        ctx.close()
    }
}
