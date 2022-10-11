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
package io.micronaut.http.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.module.SimpleModule
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.NoHostException
import io.micronaut.http.client.netty.DefaultHttpClient
import io.micronaut.jackson.annotation.JacksonFeatures
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import spock.lang.IgnoreIf
import spock.lang.Retry
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ClientScopeSpec extends Specification {

    @Retry
    void "test client scope annotation method injection"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'ClientScopeSpec',
                'from.config': '/',
                'micronaut.server.port': -1])
        embeddedServer.start()

        def applicationContext = ApplicationContext.run([
                'spec.name': 'ClientScopeSpec',
                'from.config': '/',
                'micronaut.server.port': -1,
                'micronaut.http.client.url': embeddedServer.URI,
                'micronaut.http.services.my-service.url': embeddedServer.URI,
                'micronaut.http.services.my-service-declared.url': embeddedServer.URI,
                'micronaut.http.services.my-service-declared.path': "/my-declarative-client-path",
                'micronaut.http.services.other-service.url': embeddedServer.URI,
                'micronaut.http.services.other-service.path': "/scope",
                'micronaut.http.services.other-service-2.url': embeddedServer.URI,
                'micronaut.http.services.other-service-2.path': "/scope"])
        def embeddedServer2 = applicationContext.getBean(EmbeddedServer)
        embeddedServer2.start()
        MyService myService = applicationContext.getBean(MyService)

        when:
        MyJavaService myJavaService = applicationContext.getBean(MyJavaService)

        then:
        myService.get() == 'success'
        myJavaService.client == myService.client
        myJavaService.reactiveHttpClient == myService.reactiveHttpClient

        when:"test client scope annotation field injection"
        MyServiceField myServiceField = applicationContext.getBean(MyServiceField)


        then:
        myServiceField.get() == 'success'
        myJavaService.client == myServiceField.client
        myJavaService.reactiveHttpClient == myServiceField.reactiveHttpClient

        when:"test client scope annotation constructor injection"
        MyServiceConstructor serviceConstructor = applicationContext.getBean(MyServiceConstructor)


        then:
        serviceConstructor.get() == 'success'
        myJavaService.client == serviceConstructor.client
        myJavaService.reactiveHttpClient == serviceConstructor.reactiveHttpClient


        and:"test client scope with path in annotation"
        myService.getPath() == 'success'
        myServiceField.getPath() == 'success'

        when:"test injection after declarative client"
        MyDeclarativeService client = applicationContext.getBean(MyDeclarativeService)
        client.name()

        then:
        //thrown(HttpClientException)
        Flux.from(((DefaultHttpClient) myJavaService.client)
                .resolveRequestURI(HttpRequest.GET("/foo"))).blockFirst().toString() == "http://localhost:${embeddedServer2.port}/foo"

        when:"test service definition with declarative client with jackson features"
        MyServiceJacksonFeatures jacksonFeatures = applicationContext.getBean(MyServiceJacksonFeatures)

        then:
        jacksonFeatures.name() == "success"

        when:"test service definition with declarative client with jackson features: additional module"
        MyServiceJacksonModule jacksonFeatures2 = applicationContext.getBean(MyServiceJacksonModule)

        then:
        jacksonFeatures2.bean().getFooBar() == "baz"

        when:"test no base path with the declarative client"
        NoBasePathService noBasePathService = applicationContext.getBean(NoBasePathService)

        then:
        noBasePathService.name("http://localhost:${embeddedServer.port}/scope") == "success"

        when:
        noBasePathService.name("/scope")

        then:
        def ex = thrown(NoHostException)
        ex.message == "Request URI specifies no host to connect to"

        when:"test no base path with client scope"
        HttpClient noIdClient = myService.noIdClient

        then:
        noIdClient.toBlocking().retrieve("http://localhost:${embeddedServer.port}/scope") == "success"

        when:
        noIdClient.toBlocking().retrieve("/scope")

        then:
        ex = thrown(NoHostException)
        ex.message == "Request URI specifies no host to connect to"

        when:
        InstanceEquals bean = applicationContext.getBean(InstanceEquals)
        InstanceDoesNotEqual bean2 = applicationContext.getBean(InstanceDoesNotEqual)

        then:
        bean.client.is(bean.client2)
        !bean.client.is(bean2.client) //value is different
        !bean.client2.is(bean2.client2) //bean2 has configuration

        bean.clientId.is(bean.clientId2)
        !bean.clientId.is(bean2.clientId) //id is different
        !bean.clientId2.is(bean2.clientId2) //bean2 has path
        !bean.clientId2.is(bean2.clientId3) //bean2 has configuration

        bean.clientIdPath.is(bean.clientIdPath2)
        !bean.clientIdPath.is(bean2.clientId) // path is different

        bean.clientConfiguration.is(bean.clientConfiguration2)
        !bean.clientConfiguration.is(bean2.client2) // configuration is different

        bean.rxClient.is(bean.client)

        cleanup:
        embeddedServer.close()
        embeddedServer2.close()
    }

    @Controller('/scope')
    static class ScopeController {
        @Get(produces = MediaType.TEXT_PLAIN)
        String index() {
            return "success"
        }

        @Get(produces = MediaType.APPLICATION_JSON, value = '/json')
        String json() {
            return '{"foo_bar":"baz"}'
        }
    }

    @Singleton
    static class MyService {

        @Inject @Client('${from.config}')
        HttpClient client

        @Inject @Client('${from.config}')
        HttpClient reactiveHttpClient

        @Inject @Client(id = 'myService', path = '/scope')
        HttpClient pathClient

        @Inject @Client
        HttpClient noIdClient

        String get() {
            reactiveHttpClient != null
            client.toBlocking().retrieve(
                    HttpRequest.GET('/scope'), String
            )
        }

        String getPath() {
            pathClient.toBlocking().retrieve("/", String)
        }
    }

    @Singleton
    static class MyServiceField {

        @Inject @Client('${from.config}')
        protected HttpClient client

        @Inject @Client('${from.config}')
        protected HttpClient reactiveHttpClient

        @Inject @Client(id = 'myService', path = '/scope')
        protected HttpClient pathClient

        String get() {
            reactiveHttpClient != null
            client.toBlocking().retrieve(
                    HttpRequest.GET('/scope'), String
            )
        }

        String getPath() {
            pathClient.toBlocking().retrieve("/", String)
        }
    }

    @Singleton
    static class MyServiceConstructor {

        private final HttpClient client
        private final HttpClient reactiveHttpClient

        MyServiceConstructor(@Client('${from.config}')HttpClient client,
                             @Client('${from.config}') HttpClient reactiveHttpClient) {
            this.reactiveHttpClient = reactiveHttpClient
            this.client = client
        }

        String get() {
            reactiveHttpClient != null
            client.toBlocking().retrieve(
                    HttpRequest.GET('/scope'), String
            )
        }
    }

    @Singleton
    static class InstanceEquals {

        @Inject @Client('/')
        protected HttpClient client

        @Inject @Client('/')
        protected HttpClient client2

        @Inject @Client(id = "bar")
        protected HttpClient clientId

        @Inject @Client(id = "bar")
        protected HttpClient clientId2

        @Inject @Client(id = "bar", path = "/bar")
        protected HttpClient clientIdPath

        @Inject @Client(id = "bar", path = "/bar")
        protected HttpClient clientIdPath2

        @Inject @Client(value = '/', configuration = DefaultHttpClientConfiguration)
        protected HttpClient clientConfiguration

        @Inject @Client(value = '/', configuration = DefaultHttpClientConfiguration)
        protected HttpClient clientConfiguration2

        @Inject @Client('/')
        protected HttpClient rxClient
    }

    @Singleton
    static class InstanceDoesNotEqual {

        @Inject @Client('/foo')
        protected HttpClient client

        @Inject @Client(value = '/', configuration = CustomConfig)
        protected HttpClient client2

        @Inject @Client(id = "foo")
        protected HttpClient clientId

        @Inject @Client(id = "bar", path = "/foo")
        protected HttpClient clientId2

        @Inject @Client(id = "bar", configuration = CustomConfig)
        protected HttpClient clientId3
    }

    @Singleton
    @ConfigurationProperties("custom")
    static class CustomConfig extends DefaultHttpClientConfiguration {

    }

    @Requires(property = 'spec.name', value = "ClientScopeSpec")
    @Client(id = 'my-service-declared')
    static interface MyDeclarativeService {

        @Get
        String name()
    }

    @Requires(property = 'spec.name', value = "ClientScopeSpec")
    @Client(id = 'other-service')
    @JacksonFeatures(disabledDeserializationFeatures = DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
    static interface MyServiceJacksonFeatures {

        @Get(consumes = MediaType.TEXT_PLAIN)
        String name()
    }

    @Requires(property = 'spec.name', value = "ClientScopeSpec")
    @Client(id = 'other-service-2')
    @JacksonFeatures(additionalModules = [CustomizingModule])
    static interface MyServiceJacksonModule {

        @Get(consumes = MediaType.APPLICATION_JSON, value = '/json')
        Bean bean()
    }

    static class CustomizingModule extends SimpleModule {
        @Override
        void setupModule(SetupContext context) {
            super.setupModule(context)
            ((ObjectMapper) context.getOwner()).propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
        }
    }

    static class Bean {
        private String fooBar;

        String getFooBar() {
            return fooBar
        }

        void setFooBar(String fooBar) {
            this.fooBar = fooBar
        }
    }

    @Requires(property = 'spec.name', value = "ClientScopeSpec")
    @Client
    static interface NoBasePathService {

        @Get(value = "{+uri}", consumes = MediaType.TEXT_PLAIN)
        String name(String uri)
    }
}
