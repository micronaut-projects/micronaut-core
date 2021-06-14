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
package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.management.endpoint.annotation.Endpoint
import io.micronaut.management.endpoint.annotation.Read
import io.micronaut.management.endpoint.annotation.Selector
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class EndpointsBasePathSpec extends Specification {

    def "due to the change of endpoints base path to /admin, Health endpoint is available at /admin/health"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': getClass().simpleName,
                'endpoints.all.path': '/admin',
                'endpoints.all.enabled': true
        ])
        HttpClient rxClient = server.applicationContext.createBean(HttpClient.class, server.getURL())

        when:
        rxClient.toBlocking().retrieve('/admin/health')

        then:
        noExceptionThrown()

        when:
        rxClient.toBlocking().retrieve('/health')

        then:
        def e = thrown(HttpClientResponseException)

        when:
        def response = e.response

        then:
        response.status == HttpStatus.NOT_FOUND

        cleanup:
        rxClient.close()
        server.close()
    }

    void "test routes with a server context path"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': getClass().simpleName,
                'micronaut.server.context-path': '/myapp',
                'endpoints.all.path': '/endpoints'
        ])
        HttpClient client = server.applicationContext.createBean(HttpClient.class, server.getURL())

        when:
        client.toBlocking().retrieve('/myapp/endpoints/my-endpoint/hello', String)

        then:
        def ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.UNAUTHORIZED

        cleanup:
        client.close()
        server.close()
    }

    void "test routes with a server context path and all path without leading slash"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': getClass().simpleName,
                'micronaut.server.context-path': '/myapp',
                'endpoints.all.path': 'endpoints'
        ])
        HttpClient client = server.applicationContext.createBean(HttpClient.class, server.getURL())

        when:
        client.toBlocking().retrieve('/myapp/endpoints/my-endpoint/hello', String)

        then:
        def ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.UNAUTHORIZED

        cleanup:
        client.close()
        server.close()
    }

    void "test routes with a server context path and context path without leading slash"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': getClass().simpleName,
                'micronaut.server.context-path': 'myapp',
                'endpoints.all.path': 'endpoints'
        ])
        HttpClient client = server.applicationContext.createBean(HttpClient.class, server.getURL())

        when:
        client.toBlocking().retrieve('/myapp/endpoints/my-endpoint/hello', String)

        then:
        def ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.UNAUTHORIZED

        cleanup:
        client.close()
        server.close()
    }

    void "test routes with a server context path no all path"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': getClass().simpleName,
                'micronaut.server.context-path': '/myapp',
        ])
        HttpClient client = server.applicationContext.createBean(HttpClient.class, server.getURL())

        when:
        client.toBlocking().retrieve('/myapp/my-endpoint/hello', String)

        then:
        def ex = thrown(HttpClientResponseException)
        ex.status == HttpStatus.UNAUTHORIZED

        cleanup:
        client.close()
        server.close()
    }

    @Endpoint(id = "myEndpoint")
    @Requires(property = "spec.name", value = "EndpointsBasePathSpec")
    static class MyEndpoint {

        @Read
        String name(@Selector String name) {
            name
        }
    }
}
