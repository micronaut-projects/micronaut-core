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
package io.micronaut.http.client.services

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.micronaut.http.client.DefaultHttpClient
import io.micronaut.http.client.ServiceHttpClientConfiguration
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.server.netty.NettyHttpRequest
import io.micronaut.http.ssl.ClientAuthentication
import io.micronaut.http.ssl.SslConfiguration
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.handler.ssl.SslHandler
import spock.lang.Retry
import spock.lang.Specification

import java.security.cert.X509Certificate
import java.time.Duration

@Retry // sometimes fails due to port binding on CI, so retry
class ManualHttpServiceDefinitionSpec extends Specification {


    void "test that manually defining an HTTP client creates a client bean"() {
        given:
        EmbeddedServer firstApp = ApplicationContext.run(EmbeddedServer)


        ApplicationContext clientApp = ApplicationContext.run(
                'micronaut.http.services.foo.url': firstApp.getURI(),
                'micronaut.http.services.foo.path': '/manual/http/service',
                'micronaut.http.services.foo.health-check':true,
                'micronaut.http.services.foo.health-check-interval':'100ms',
                'micronaut.http.services.foo.read-timeout':'15s',
                'micronaut.http.services.foo.pool.enabled':false,
                "micronaut.http.services.foo.ssl.enabled": false,
                "micronaut.http.services.foo.ssl.client-authentication": "NEED",
                "micronaut.http.services.foo.ssl.key-store.path": "classpath:foo",
                "micronaut.http.services.foo.ssl.key-store.password": "secret",
                'micronaut.http.services.bar.url': firstApp.getURI(),
                'micronaut.http.services.bar.path': '/manual/http/service',
                'micronaut.http.services.bar.health-check':true,
                'micronaut.http.services.bar.health-check-interval':'100ms',
                'micronaut.http.services.bar.read-timeout':'10s',
                'micronaut.http.services.bar.pool.enabled':true,
                "micronaut.http.services.bar.ssl.enabled": false,
                "micronaut.http.services.bar.ssl.client-authentication": "NEED",
                "micronaut.http.services.bar.ssl.key-store.path": "classpath:bar",
                "micronaut.http.services.bar.ssl.key-store.password": "secret",
                'micronaut.http.services.baz.url': firstApp.getURI(),
                'micronaut.http.services.baz.path': '/manual/http/service',
        )
        TestClientFoo tcFoo = clientApp.getBean(TestClientFoo)
        TestClientBar tcBar = clientApp.getBean(TestClientBar)

        when:'the config is retrieved'
        def config = clientApp.getBean(HttpClientConfiguration, Qualifiers.byName("foo"))

        then:
        config.readTimeout.get() == Duration.ofSeconds(15)
        !config.connectionPoolConfiguration.enabled
        !config.sslConfiguration.enabled
        config.sslConfiguration.keyStore.password.get() == "secret"
        config.sslConfiguration.keyStore.path.get() == "classpath:foo"
        config.sslConfiguration.clientAuthentication.get() == ClientAuthentication.NEED


        when:
        RxHttpClient client = clientApp.getBean(RxHttpClient, Qualifiers.byName("foo"))
        String result = client.retrieve('/').blockingFirst()

        then:
        client.configuration == config
        result == 'ok'
        tcFoo.index() == 'ok'

        when:'the config is retrieved'
        config = clientApp.getBean(HttpClientConfiguration, Qualifiers.byName("bar"))

        then:
        config.readTimeout.get() == Duration.ofSeconds(10)
        config.getConnectionPoolConfiguration().isEnabled()
        !config.sslConfiguration.enabled
        config.sslConfiguration.keyStore.password.get() == "secret"
        config.sslConfiguration.keyStore.path.get() == "classpath:bar"
        config.sslConfiguration.clientAuthentication.get() == ClientAuthentication.NEED

        when:
        client = clientApp.getBean(RxHttpClient, Qualifiers.byName("bar"))
        result = client.retrieve(HttpRequest.POST('/', '')).blockingFirst()

        then:
        client.configuration == config
        result == 'created'
        tcBar.save() == 'created'
        tcBar.update() == "updated"

        when: "a client that overrides the path is used"
        TestClientBaz tcBaz = clientApp.getBean(TestClientBaz)

        then:
        tcBaz.index() == "ok-other"
        tcBaz.save() == "created-other"
        tcBaz.update() == "updated-other"

        cleanup:
        firstApp.close()
        clientApp.close()
    }

    void 'test default client ssl configuration'() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'micronaut.http.services.foo.ssl.enabled':true,
                'micronaut.http.services.foo.ssl.key.password':'blah',
                'micronaut.http.services.foo.ssl.key-store.path':'blahpath',
                'micronaut.http.services.foo.ssl.trust-store.path':'blahtrust',
        )

        ServiceHttpClientConfiguration clientConfiguration = ctx.getBean(ServiceHttpClientConfiguration, Qualifiers.byName("foo"))
        SslConfiguration sslConfiguration = clientConfiguration.getSslConfiguration()

        expect:
        clientConfiguration
        sslConfiguration.isEnabled()
        sslConfiguration.getTrustStore().getPath().get() == 'blahtrust'
        sslConfiguration.getKeyStore().getPath().get() == 'blahpath'

        cleanup:
        ctx.close()
    }

    void "test that manually defining an HTTP client without URL doesn't create bean"() {
        given:
        ApplicationContext clientApp = ApplicationContext.run(
                'micronaut.http.services.foo.path': '/manual/http/service',
                'micronaut.http.services.foo.health-check':true,
                'micronaut.http.services.foo.health-check-interval':'100ms',
                'micronaut.http.services.foo.read-timeout':'15s',
                'micronaut.http.services.foo.pool.enabled':false
        )

        when:'the config is retrieved'
        def config = clientApp.getBean(HttpClientConfiguration, Qualifiers.byName("foo"))

        then:
        config.readTimeout.get() == Duration.ofSeconds(15)
        !config.getConnectionPoolConfiguration().isEnabled()

        when:
        def opt = clientApp.findBean(RxHttpClient, Qualifiers.byName("foo"))

        then:
        !opt.isPresent()

        cleanup:
        clientApp.close()
    }

    void "test working SSL configuration"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.ssl.enabled': true,
                'micronaut.server.ssl.client-authentication': 'NEED',
                'micronaut.server.ssl.key-store.path': 'classpath:certs/server.p12',
                'micronaut.server.ssl.key-store.password': 'secret',
                'micronaut.server.ssl.trust-store.path': 'classpath:certs/truststore',
                'micronaut.server.ssl.trust-store.password': 'secret'
        ])

        ApplicationContext ctx = ApplicationContext.run(
                'micronaut.http.services.client1.urls': ['https://localhost:8443'],
                'micronaut.http.services.client1.path': ['/ssl-test'],
                'micronaut.http.services.client1.ssl.enabled': true,
                'micronaut.http.services.client1.ssl.client-authentication': 'NEED',
                'micronaut.http.services.client1.ssl.key-store.path': 'classpath:certs/client1.p12',
                'micronaut.http.services.client1.ssl.key-store.password': 'secret'
        )
        SslClient client1 = ctx.getBean(SslClient)
        final String DN = "CN=client1.test.example.com, OU=IT, O=Whatever, L=Munich, ST=Bavaria, C=DE, EMAILADDRESS=info@example.com"


        when:
        def client = new DefaultHttpClient(embeddedServer.getURL(), ctx.getBean(HttpClientConfiguration, Qualifiers.byName("client1")))

        then:
        client.toBlocking().retrieve(HttpRequest.GET("/ssl-test"), String) == DN

        when:
        client = ctx.getBean(RxHttpClient, Qualifiers.byName("client1"))

        then:
        client.toBlocking().retrieve(HttpRequest.GET("/"), String) == DN

        expect:
        client1.test().body() == "CN=client1.test.example.com, OU=IT, O=Whatever, L=Munich, ST=Bavaria, C=DE, EMAILADDRESS=info@example.com"

        cleanup:
        ctx.close()
        embeddedServer.close()
    }

    @Client(id = "foo")
    static interface TestClientFoo {
        @Get
        String index()
    }

    @Client("bar")
    static interface TestClientBar {
        @Post
        String save()

        @Put("update")
        String update()
    }

    @Client(id = "baz", path = "/other/http/service")
    static interface TestClientBaz {
        @Get
        String index()

        @Post
        String save()

        @Put("update")
        String update()
    }

    @Controller('manual/http/service')
    static class TestController {
        @Get
        String index() {
            return "ok"
        }

        @Post
        String save() {
            return "created"
        }

        @Put("update")
        String update() {
            return "updated"
        }
    }

    @Controller('other/http/service')
    static class OtherController {
        @Get
        String index() {
            return "ok-other"
        }

        @Post
        String save() {
            return "created-other"
        }

        @Put("update")
        String update() {
            return "updated-other"
        }
    }

    @Controller("/ssl-test")
    static class SslController {

        @Get
        HttpResponse<String> test(HttpRequest request) {
            def pipeline = (request as NettyHttpRequest).getChannelHandlerContext().pipeline()
            def sslHandler = pipeline.get(SslHandler)
            def certs = sslHandler.engine().getSession().getPeerCertificates()

            def cert = certs.first() as X509Certificate

            return HttpResponse.ok(cert.subjectDN.name)
        }
    }

    @Client("client1")
    static interface SslClient {
        @Get
        HttpResponse<String> test()
    }
}
