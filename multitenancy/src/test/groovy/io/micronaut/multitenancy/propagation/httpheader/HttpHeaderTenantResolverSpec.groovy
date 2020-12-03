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
package io.micronaut.multitenancy.propagation.httpheader

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.multitenancy.propagation.TenantPropagationHttpClientFilter
import io.micronaut.multitenancy.tenantresolver.CookieTenantResolver
import io.micronaut.multitenancy.tenantresolver.HttpHeaderTenantResolver
import io.micronaut.multitenancy.tenantresolver.HttpRequestTenantResolver
import io.micronaut.multitenancy.tenantresolver.TenantResolver
import io.micronaut.multitenancy.writer.HttpHeaderTenantWriter
import io.micronaut.multitenancy.writer.TenantWriter
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@Stepwise
class HttpHeaderTenantResolverSpec extends Specification {

    static final SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    int gormPort

    @AutoCleanup
    @Shared
    EmbeddedServer gormEmbeddedServer

    @AutoCleanup
    @Shared
    RxHttpClient gormClient

    @AutoCleanup
    @Shared
    EmbeddedServer gatewayEmbeddedServer

    @AutoCleanup
    @Shared
    RxHttpClient gatewayClient

    def setupSpec() {
        gormPort = SocketUtils.findAvailableTcpPort()
    }

    def "setup gorm server"() {
        given:
        Map gormConfig = [
                'micronaut.server.port'                       : gormPort,
                (SPEC_NAME_PROPERTY)                          : 'multitenancy.httpheader.gorm',
                'micronaut.multitenancy.tenantresolver.httpheader.enabled': true
        ]

        gormEmbeddedServer = ApplicationContext.run(EmbeddedServer, gormConfig, Environment.TEST)

        gormClient = gormEmbeddedServer.applicationContext.createBean(RxHttpClient, gormEmbeddedServer.getURL())

        when:
        for (Class beanClazz : [BookService, BooksController, Bootstrap]) {
            gormEmbeddedServer.applicationContext.getBean(beanClazz)
        }

        then:
        noExceptionThrown()

        and:
        gormEmbeddedServer.applicationContext.containsBean(TenantResolver)
        gormEmbeddedServer.applicationContext.getBean(TenantResolver) instanceof HttpHeaderTenantResolver
        gormEmbeddedServer.applicationContext.containsBean(HttpRequestTenantResolver)
        gormEmbeddedServer.applicationContext.getBean(HttpRequestTenantResolver) instanceof HttpHeaderTenantResolver
    }

    def "setup gateway server"() {
        given:
        Map gatewayConfig = [
                (SPEC_NAME_PROPERTY): 'multitenancy.httpheader.gateway',
                'micronaut.http.services.books.url': "http://localhost:${gormPort}",
                'micronaut.multitenancy.propagation.enabled': true,
                'micronaut.multitenancy.propagation.service-id-regex': 'books',
                'micronaut.multitenancy.tenantwriter.httpheader.enabled': true,
                'micronaut.multitenancy.tenantresolver.httpheader.enabled': true
        ]

        gatewayEmbeddedServer = ApplicationContext.run(EmbeddedServer, gatewayConfig, Environment.TEST)

        when:
        for (Class beanClazz : [
                GatewayController,
                BooksClient,
                HttpHeaderTenantWriter,
                HttpHeaderTenantResolver,
                TenantWriter,
                TenantResolver,
                TenantPropagationHttpClientFilter
        ]) {
            gatewayEmbeddedServer.applicationContext.getBean(beanClazz)
        }

        then:
        noExceptionThrown()

        when:
        gatewayClient = gatewayEmbeddedServer.applicationContext.createBean(RxHttpClient, gatewayEmbeddedServer.getURL())

        then:
        noExceptionThrown()
    }

    def "fetch books for watson and sherlock directly from the books microservice, the tenantId is in the HTTP Header. They get only their books"() {
        when:
        HttpResponse rsp = gormClient.toBlocking().exchange(HttpRequest.GET('/api/books')
                .header("tenantId", "sherlock"), Argument.of(List, Book))

        then:
        rsp.status() == HttpStatus.OK
        rsp.body().size() == 1
        ['Sherlock diary'] == rsp.body()*.title

        when:
        rsp = gormClient.toBlocking().exchange(HttpRequest.GET('/api/books')
                .header("tenantId", "watson"), Argument.of(List, Book))

        then:
        rsp.status() == HttpStatus.OK
        rsp.body().size() == 1
        ['Watson diary'] == rsp.body()*.title
    }

    def "fetch books for watson and sherlock, since the tenant ID is in the HTTP header and its propagated. They get only their books"() {
        when:
        HttpResponse rsp = gatewayClient.toBlocking().exchange(HttpRequest.GET('/')
                .header("tenantId", "sherlock"), Argument.of(List, Book))

        then:
        rsp.status() == HttpStatus.OK
        rsp.body()
        ['Sherlock diary'] == rsp.body()*.title

        when:
        rsp = gatewayClient.toBlocking().exchange(HttpRequest.GET('/')
                .header("tenantId", "watson"), Argument.of(List, Book))

        then:
        rsp.status() == HttpStatus.OK
        rsp.body().size() == 1
        ['Watson diary'] == rsp.body()*.title
    }
}
