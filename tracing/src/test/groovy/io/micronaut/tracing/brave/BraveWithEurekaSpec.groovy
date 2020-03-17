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
package io.micronaut.tracing.brave

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpMethod
import io.micronaut.http.annotation.Post
import io.micronaut.web.router.Router
import spock.lang.Issue
import spock.lang.Specification

class BraveWithEurekaSpec extends Specification {

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/811')
    void "test that brave does not cause early init of beans"() {

        given:
        ApplicationContext ctx = ApplicationContext.builder()
            .properties(
                'eureka.client.registration.enabled':true,
                'eureka.client.defaultZone':'${EUREKA_HOST:localhost}:${EUREKA_PORT:9001}',
                'tracing.zipkin.http.url': 'http://localhost:9411',
                'tracing.zipkin.enabled':true

        ).build().start()

        expect:
        ctx.getBean(Router).route(HttpMethod.POST, '/api/v2/spans').isPresent()

        cleanup:
        ctx.close()
    }
}
