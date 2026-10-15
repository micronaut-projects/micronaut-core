package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.PropertySource
import io.micronaut.http.BasicHttpAttributes
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Covers the per-method data that the client introduction advice caches across calls.
 */
class ClientMethodCacheSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'ClientMethodCacheSpec',
            'method-cache.path': '/first',
            'method-cache.prefix': '/method-cache'
    ])

    @Shared
    ApplicationContext ctx = server.applicationContext

    def setup() {
        ctx.getBean(TemplateCapturingFilter).templates.clear()
    }

    void "methods of the same client keep their own URI template across calls"() {
        given:
        CacheClient client = ctx.getBean(CacheClient)
        TemplateCapturingFilter filter = ctx.getBean(TemplateCapturingFilter)

        expect:
        (0..<3).every {
            client.one("a$it") == "one:a$it" &&
                    client.two("b$it", "c$it") == "two:b$it:c$it" &&
                    client.query("q$it") == "query:q$it"
        }
        filter.templates.toList() == ['/method-cache/one/{id}', '/method-cache/two/{a}/{b}', '/method-cache/query{?q}'] * 3
    }

    void "the client path is combined with the method URI"() {
        given:
        PathClient client = ctx.getBean(PathClient)
        TemplateCapturingFilter filter = ctx.getBean(TemplateCapturingFilter)

        expect:
        client.one("x") == "one:x"
        client.one("y") == "one:y"
        filter.templates.toList() == ['/method-cache/one/{id}', '/method-cache/one/{id}']
    }

    void "media types and the body are bound on every call"() {
        given:
        CacheClient client = ctx.getBean(CacheClient)

        expect:
        client.text("hello") == "text/plain:hello"
        client.text("world") == "text/plain:world"
        client.json(new Payload(name: "a")) == "application/json:a"
        client.json(new Payload(name: "b")) == "application/json:b"
        client.header("v1") == "header:v1"
        client.header("v2") == "header:v2"
    }

    void "a method URI with a property placeholder follows the environment"() {
        given:
        PlaceholderClient client = ctx.getBean(PlaceholderClient)
        TemplateCapturingFilter filter = ctx.getBean(TemplateCapturingFilter)

        expect:
        client.get() == "first"
        client.get() == "first"

        when:
        ctx.environment.addPropertySource(PropertySource.of("method-cache-override", ['method-cache.path': '/second'], 1000))

        then:
        client.get() == "second"
        client.get() == "second"
        filter.templates.toList() == ['/method-cache/first', '/method-cache/first', '/method-cache/second', '/method-cache/second']
    }

    static class Payload {
        String name
    }

    @Requires(property = 'spec.name', value = 'ClientMethodCacheSpec')
    @Client('/method-cache')
    static interface CacheClient {

        @Get('/one/{id}')
        String one(String id)

        @Get('/two/{a}/{b}')
        String two(String a, String b)

        @Get('/query{?q}')
        String query(String q)

        @Post('/echo-type')
        @Produces(MediaType.TEXT_PLAIN)
        @Consumes(MediaType.TEXT_PLAIN)
        String text(@Body String body)

        @Post('/echo-json')
        @Consumes(MediaType.TEXT_PLAIN)
        String json(@Body Payload body)

        @Get('/header')
        @Consumes(MediaType.TEXT_PLAIN)
        String header(@Header('X-Value') String value)
    }

    @Requires(property = 'spec.name', value = 'ClientMethodCacheSpec')
    @Client(value = '/', path = '/method-cache')
    static interface PathClient {

        @Get('/one/{id}')
        String one(String id)
    }

    @Requires(property = 'spec.name', value = 'ClientMethodCacheSpec')
    @Client('${method-cache.prefix}')
    static interface PlaceholderClient {

        @Get('${method-cache.path}')
        @Consumes(MediaType.TEXT_PLAIN)
        String get()
    }

    @Requires(property = 'spec.name', value = 'ClientMethodCacheSpec')
    @ClientFilter('/method-cache/**')
    static class TemplateCapturingFilter {

        final Queue<String> templates = new ConcurrentLinkedQueue<>()

        @RequestFilter
        void filter(HttpRequest<?> request) {
            templates.add(BasicHttpAttributes.getUriTemplate(request).orElse(null))
        }
    }

    @Requires(property = 'spec.name', value = 'ClientMethodCacheSpec')
    @Controller('/method-cache')
    static class CacheController {

        @Get('/one/{id}')
        String one(String id) {
            "one:$id"
        }

        @Get('/two/{a}/{b}')
        String two(String a, String b) {
            "two:$a:$b"
        }

        @Get('/query{?q}')
        String query(String q) {
            "query:$q"
        }

        @Post(value = '/echo-type', consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        String echoType(HttpRequest<String> request, @Body String body) {
            "${request.contentType.get()}:$body"
        }

        @Post(value = '/echo-json', produces = MediaType.TEXT_PLAIN)
        String echoJson(HttpRequest<?> request, @Body Payload body) {
            "${request.contentType.get()}:${body.name}"
        }

        @Get(value = '/header', produces = MediaType.TEXT_PLAIN)
        String header(@Header('X-Value') String value) {
            "header:$value"
        }

        @Get(value = '/first', produces = MediaType.TEXT_PLAIN)
        String first() {
            "first"
        }

        @Get(value = '/second', produces = MediaType.TEXT_PLAIN)
        String second() {
            "second"
        }
    }
}
