package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.ServiceInstanceList
import io.micronaut.http.BasicAuth
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.*
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Specification

class ClientIntroductionAdviceSpec extends Specification {

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'ClientIntroductionAdviceSpec',
    ])

    void "test implement HTTP client"() {
        given:
        MyClient myService = server.applicationContext.getBean(MyClient)

        expect:
        myService.index() == 'success'
    }

    void "service id appears in exceptions"() {
        given:
        server.applicationContext.registerSingleton(new TestServiceInstanceList(server.getURI()))
        PolicyClient myService = server.applicationContext.getBean(PolicyClient)

        when:
        myService.failure()

        then:
        def e = thrown(HttpClientResponseException)
        e.serviceId == 'test-service'
        e.message == "Client 'test-service': Bad Request"
    }

    void "test multiple clients with the same id and different paths"() {
        given:
        server.applicationContext.registerSingleton(new TestServiceInstanceList(server.getURI()))

        expect:
        server.applicationContext.getBean(PolicyClient).index() == 'policy'
        server.applicationContext.getBean(OfferClient).index() == 'offer'
    }

    void "test a client with a body and header"() {
        given:
        server.applicationContext.registerSingleton(new TestServiceInstanceList(server.getURI()))

        when:
        OfferClient client = server.applicationContext.getBean(OfferClient)

        then:
        client.post('abc', 'bar') == 'abc header=bar'
    }

    void "test a client that auto encodes basic auth header"() {
        given:
        server.applicationContext.registerSingleton(new TestServiceInstanceList(server.getURI()))

        when:
        BasicAuthHeaderAutoEncodingClient client = server.applicationContext.getBean(BasicAuthHeaderAutoEncodingClient)

        then:
        client.post('abc', new BasicAuth("username", "password")) == 'abc basic-auth-header=Basic dXNlcm5hbWU6cGFzc3dvcmQ='
    }

    void "test non body params have preference for uri templates"() {
        when:
        LocalOfferClient client = server.applicationContext.getBean(LocalOfferClient)

        then:
        client.putTest("abc", new MyObject(code: "def")) == "abc"
    }

    void "test basic auth"() {
        when:
        ApplicationContext ctx = ApplicationContext.run(['spec.name': 'ClientIntroductionAdviceSpec', 'server-port': server.port])
        BasicAuthClient client = ctx.getBean(BasicAuthClient)

        then:
        client.get() == 'config:secret'

        cleanup:
        ctx.close()
    }

    void "test execution of a default method"() {
        given:
        DefaultMethodClient myService = server.applicationContext.getBean(DefaultMethodClient)

        expect:
        myService.defaultMethod() == 'success from default method mutated'
    }

    void "test execution of a default method 2"() {
        given:
        DefaultMethodClient2 myService = server.applicationContext.getBean(DefaultMethodClient2)

        expect:
        myService.index("ZZZ") == 'success ZZZ XYZ from default method'
        myService.defaultMethod() == 'success from default method mutated'
        myService.defaultMethod2("ABC") == 'success ABC XYZ from default method 2 mutated'
    }

    void "test execution of a default method 3"() {
        given:
        DefaultMethodClient3 myService = server.applicationContext.getBean(DefaultMethodClient3)

        expect:
        myService.index("ZZZ") == 'success ZZZ XYZ from default method'
        myService.defaultMethod() == 'success from default method mutated'
        myService.defaultMethod2("ABC") == 'success ABC XYZ from default method 2 mutated'
    }

    @Requires(property = 'spec.name', value = 'ClientIntroductionAdviceSpec')
    @Controller('/aop')
    static class AopController implements MyApi {
        @Override
        String index() {
            return "success"
        }
    }

    @Requires(property = 'spec.name', value = 'ClientIntroductionAdviceSpec')
    @Controller('/policies')
    static class PolicyController {
        @Get
        String index() {
            "policy"
        }

        @Get('/failure')
        HttpResponse<?> failure() {
            return HttpResponse.badRequest()
        }
    }

    @Requires(property = 'spec.name', value = 'ClientIntroductionAdviceSpec')
    @Controller('/offers')
    static class OfferController {
        @Get
        String index() {
            "offer"
        }

        @Post(produces = MediaType.TEXT_PLAIN, consumes = MediaType.TEXT_PLAIN)
        String post(@Body String data, @Header String foo)  {
            return data + ' header=' + foo
        }

        @Put("/{code}")
        String code(String code) {
            code
        }
    }

    @Requires(property = 'spec.name', value = 'ClientIntroductionAdviceSpec')
    @Controller('/encoded-basic-auth')
    static class EncodedBasicAuthController {
        @Post(produces = MediaType.TEXT_PLAIN, consumes = MediaType.TEXT_PLAIN)
        String post(@Body String data, @Header String authorization)  {
            return data + " basic-auth-header=${authorization}"
        }
    }

    @Requires(property = 'spec.name', value = 'ClientIntroductionAdviceSpec')
    @Controller("/basic-auth")
    static class BasicAuthController {

        @Get
        String index(BasicAuth basicAuth) {
            basicAuth.getUsername() + ":" + basicAuth.getPassword()
        }
    }

    static interface MyApi {
        @Get(produces = MediaType.TEXT_PLAIN, consumes = MediaType.TEXT_PLAIN)
        String index()
    }

    @Requires(property = 'spec.name', value = 'ClientIntroductionAdviceSpec')
    @Client('/aop')
    static interface MyClient extends MyApi {
    }

    @Requires(property = 'spec.name', value = 'ClientIntroductionAdviceSpec')
    @Client(id="test-service", path="/policies")
    static interface PolicyClient {
        @Get
        String index()

        @Get('/failure')
        String failure()
    }

    @Requires(property = 'spec.name', value = 'ClientIntroductionAdviceSpec')
    @Client(id="test-service", path="/offers")
    static interface OfferClient {
        @Get
        String index()

        @Post(produces = MediaType.TEXT_PLAIN, consumes = MediaType.TEXT_PLAIN)
        String post(@Body String data, @Header String foo)
    }

    @Requires(property = 'spec.name', value = 'ClientIntroductionAdviceSpec')
    @Client("/offers")
    static interface LocalOfferClient {

        @Put("/{code}")
        String putTest(String code, @Body MyObject myObject)
    }

    @Requires(property = 'spec.name', value = 'ClientIntroductionAdviceSpec')
    @Client('http://config:secret@localhost:${server-port}/basic-auth')
    static interface BasicAuthClient {

        @Get
        String get()
    }

    @Requires(property = 'spec.name', value = 'ClientIntroductionAdviceSpec')
    @Client(id="test-service", path="/encoded-basic-auth")
    static interface BasicAuthHeaderAutoEncodingClient {
        @Post(produces = MediaType.TEXT_PLAIN, consumes = MediaType.TEXT_PLAIN)
        String post(@Body String data, BasicAuth basicAuth)
    }

    @Introspected
    static class MyObject {
        String code
    }

    class TestServiceInstanceList implements ServiceInstanceList {

        private final URI uri

        TestServiceInstanceList(URI uri) {
            this.uri = uri
        }

        @Override
        String getID() {
            return "test-service"
        }

        @Override
        List<ServiceInstance> getInstances() {
            [ServiceInstance.of("test-service-1", uri)]
        }
    }

}
