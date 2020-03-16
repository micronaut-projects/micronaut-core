package io.micronaut.http.client.http2

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.docs.server.json.Person
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.RxStreamingHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.sse.Event
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration
import java.time.temporal.ChronoUnit

// Netty + HTTP/2 on JDKs less than 9 require tcnative setup
// which is not included in this test suite
//@IgnoreIf({ !Jvm.current.isJava9Compatible() })
class Http2RequestSpec extends Specification {
    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'micronaut.ssl.enabled': true,
            "micronaut.server.http-version" : "2.0",
            "micronaut.http.client.http-version" : "2.0",
            'micronaut.ssl.buildSelfSigned': true,
            'micronaut.ssl.port': -1,
            "micronaut.http.client.log-level" : "TRACE",
            "micronaut.server.netty.log-level" : "TRACE"
    ])
    RxHttpClient client = server.getApplicationContext().getBean(RxHttpClient)

    void "test make HTTP/2 stream request"() {
        when:"A non stream request is executed"
        def people = ((RxStreamingHttpClient)client).jsonStream(HttpRequest.GET("${server.URL}/http2/personStream"), Person)
                .toList().blockingGet()

        then:
        people == Http2Controller.people

        when:"posting a data"
        def response = client.exchange(HttpRequest.POST("${server.URL}/http2/personStream", Http2Controller.people), Argument.listOf(Person))
                .blockingFirst()

        then:"The response is correct"
        response.body() == Http2Controller.people

    }

    void "test make HTTP/2 sse stream request"() {
        when:"An sse stream is obtain"
        def client = server.applicationContext.getBean(TestHttp2Client)

        def results = client.rich().toIterable().toList()

        then:
        results.size() == 4
        results[0].data.firstName == "First 1"
        results[0].id == "1"
        results.every { it.data instanceof Person && it.data.firstName }
    }

    void "test make HTTP/2 request - HTTPS"() {
        when:
        def result = client.retrieve("${server.URL}/http2").blockingFirst()

        then:
        result == 'Version: HTTP_2_0'

        when:"operation repeated to use same connection"
        result = client.retrieve("${server.URL}/http2").blockingFirst()

        then:
        result == 'Version: HTTP_2_0'

        when:"A non stream request is executed"
        List<Person> people = client.retrieve(HttpRequest.GET("${server.URL}/http2/personStream"), Argument.listOf(Person))
                .blockingFirst()

        then:
        people == Http2Controller.people

    }

    void "test make HTTP/2 request - upgrade over HTTP"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                "micronaut.server.http-version" : "2.0",
                "micronaut.http.client.http-version" : "2.0",
                "micronaut.http.client.read-timeout": -1,
                "micronaut.http.client.log-level" : "TRACE",
                "micronaut.server.netty.log-level" : "TRACE"
        ])
        RxHttpClient client = server.getApplicationContext().getBean(RxHttpClient)

        when:
        def result = client.retrieve("${server.URL}/http2").blockingFirst()

        then:
        result == 'Version: HTTP_2_0'

        when:"operation repeated to use same connection"
        result = client.retrieve("${server.URL}/http2").blockingFirst()

        then:
        result == 'Version: HTTP_2_0'

        cleanup:
        server.close()
    }


    void "test HTTP/2 server with HTTP/1 client request works"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.ssl.enabled': true,
                "micronaut.server.http-version" : "2.0",
                'micronaut.ssl.buildSelfSigned': true,
                'micronaut.ssl.port': -1,
                "micronaut.http.client.log-level" : "TRACE",
                "micronaut.server.netty.log-level" : "TRACE"
        ])
        RxHttpClient client = server.getApplicationContext().getBean(RxHttpClient)
        def result = client.retrieve("${server.URL}/http2").blockingFirst()

        expect:
        result == 'Version: HTTP_1_1'

        cleanup:
        server.close()
    }


    @Controller("/http2")
    static class Http2Controller {


        public static final List<Person> people = [new Person("Fred", "Flinstone"), new Person("Barney", "Rubble")]

        @Get(produces = MediaType.TEXT_HTML)
        String index(HttpRequest<?> request) {
            return "Version: ${request.httpVersion}"
        }

        @Get(value = '/stream', produces = MediaType.TEXT_PLAIN)
        Flowable<String> flowable(HttpRequest<?> request) {
            return Flowable.fromIterable(
                    ["Version: ",
                    request.httpVersion.toString()]
            )
        }

        @Get(value = '/personStream', produces = MediaType.APPLICATION_JSON)
        Flowable<Person> personStream(HttpRequest<?> request) {
            return Flowable.fromIterable(
                    people
            )
        }

        @Post(value = '/personStream', processes = MediaType.APPLICATION_JSON)
        Flowable<Person> postStream(@Body Flowable<Person> body) {
            return Flowable.fromIterable(
                    people
            )
        }

        @Get('/sse/rich')
        @Produces(MediaType.TEXT_EVENT_STREAM)
        Publisher<Event> rich() {
            Integer i = 0
            Flowable.generate( { io.reactivex.Emitter<Event> emitter ->
                if (i < 4) {
                    i++
                    emitter.onNext(
                            Event.of(new Person( "First $i","Last $i"))
                                    .name('foo')
                                    .id(i.toString())
                                    .comment("Foo Comment $i")
                                    .retry(Duration.of(2, ChronoUnit.MINUTES)))
                }
                else {
                    emitter.onComplete()
                }
            })
        }
    }

    @Client('/http2/sse')
    static interface TestHttp2Client {

        @Get(value = '/rich', processes = MediaType.TEXT_EVENT_STREAM)
        Flux<Event<Person>> rich()
    }
}
