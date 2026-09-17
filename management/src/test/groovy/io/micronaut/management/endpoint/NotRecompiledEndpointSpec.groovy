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
package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultBeanDefinitionsProvider
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.management.endpoint.annotation.Endpoint
import io.micronaut.management.endpoint.annotation.Read
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * {@code @Endpoint} became {@code @Indexed(Endpoint)} in 5.2, so an endpoint compiled against an earlier
 * version has no {@code Endpoint} index in its bean definition reference. Every released module that
 * ships an endpoint, such as the metrics endpoint of micronaut-micrometer, is compiled that way, and
 * resolving endpoints from the index alone lost them: their routes answered 404.
 */
class NotRecompiledEndpointSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = startServer()

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "test the reference is shaped like one compiled before @Endpoint was indexed"() {
        expect:
        BeanDefinitionReference reference = server.applicationContext.beanDefinitionReferences.find { it instanceof NotIndexedReference }
        reference.beanType == NotRecompiled
        !(Endpoint in reference.indexes)
    }

    void "test an endpoint without the index is still enumerated by its stereotype"() {
        expect:
        NotRecompiled in server.applicationContext.getBeanDefinitions(Qualifiers.byStereotype(Endpoint))*.beanType
    }

    void "test an endpoint without the index still has its route"() {
        when:
        def response = client.toBlocking().exchange("/not-recompiled", String)

        then:
        response.code() == HttpStatus.OK.code
        response.body() == 'not recompiled'
    }

    private static EmbeddedServer startServer() {
        ApplicationContext context = ApplicationContext.builder(Environment.TEST)
                .beanDefinitionsProvider { ClassLoader classLoader ->
                    NotIndexedReference.wrap(new DefaultBeanDefinitionsProvider().provide(classLoader), NotRecompiled, Endpoint)
                }
                .start()
        return context.getBean(EmbeddedServer).start()
    }
}

@Endpoint(id = 'notRecompiled', defaultSensitive = false)
class NotRecompiled {

    @Read
    String value() {
        'not recompiled'
    }
}
