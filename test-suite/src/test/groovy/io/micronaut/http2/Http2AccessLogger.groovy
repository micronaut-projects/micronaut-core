package io.micronaut.http2

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.docs.server.json.Person
import io.micronaut.http.HttpRequest
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
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

// Netty + HTTP/2 on JDKs less than 9 require tcnative setup
// which is not included in this test suite
//@IgnoreIf({ !Jvm.current.isJava9Compatible() })
class Http2AccessLoggerSpec extends Specification {
    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'micronaut.ssl.enabled': true,
            "micronaut.server.http-version" : "2.0",
            "micronaut.http.client.http-version" : "2.0",
            'micronaut.ssl.buildSelfSigned': true,
            'micronaut.ssl.port': -1,
            "micronaut.http.client.log-level" : "TRACE",
            "micronaut.server.netty.log-level" : "TRACE",
            'micronaut.server.netty.access-logger.enabled': true
    ])
    RxHttpClient client = server.getApplicationContext().getBean(RxHttpClient)
    static MemoryAppender appender = new MemoryAppender()

    static {
        Logger l = (Logger) LoggerFactory.getLogger("HTTP_ACCESS_LOGGER")
        l.addAppender(appender)
        appender.start()
    }

    void "test make HTTP/2 stream request with access logger enabled"() {
        when:"A non stream request is executed"
        appender.events.clear()
        def people = ((RxStreamingHttpClient)client).jsonStream(HttpRequest.GET("${server.URL}/http2/personStream"), Person)
                .toList().blockingGet()

        then:
        people
        appender.headLog(10)

        when:"posting a data"
        def response = client.exchange(HttpRequest.POST("${server.URL}/http2/personStream", Object), Argument.listOf(Person))
                .blockingFirst()

        then:
        response
        appender.headLog(10)

    }

    void "test make HTTP/2 sse stream request with access logger enabled"() {
        when:"An sse stream is obtain"
        def client = server.applicationContext.getBean(TestHttp2Client)
        appender.events.clear()
        def results = client.rich().toList().blockingGet()

        then:
        results.size() == 4
        appender.headLog(10)
    }

    void "test make HTTP/2 request with access logger enabled - HTTPS"() {
        when:
        appender.events.clear()
        def result = client.retrieve("${server.URL}/http2").blockingFirst()

        then:
        result == 'Version: HTTP_2_0'
        appender.headLog(10)

        when:"operation repeated to use same connection"
        result = client.retrieve("${server.URL}/http2").blockingFirst()

        then:
        result == 'Version: HTTP_2_0'
        appender.headLog(10)

        when:"A non stream request is executed"
        client.retrieve(HttpRequest.GET("${server.URL}/http2/personStream"), Argument.listOf(Person))
                .blockingFirst()

        then:
        appender.headLog(10)
    }

    void "test make HTTP/2 request with access logger enabled - upgrade over HTTP"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                "micronaut.server.http-version" : "2.0",
                "micronaut.http.client.http-version" : "2.0",
                "micronaut.http.client.read-timeout": -1,
                "micronaut.http.client.log-level" : "TRACE",
                "micronaut.server.netty.log-level" : "TRACE",
                'micronaut.server.netty.access-logger.enabled': true
        ])
        RxHttpClient client = server.getApplicationContext().getBean(RxHttpClient)
        appender.events.clear()

        when:
        def result = client.retrieve("${server.URL}/http2").blockingFirst()

        then:
        result == 'Version: HTTP_2_0'
        appender.headLog(10)

        when:"operation repeated to use same connection"
        result = client.retrieve("${server.URL}/http2").blockingFirst()

        then:
        result == 'Version: HTTP_2_0'
        appender.headLog(10)

        cleanup:
        server.close()
    }

    void "test HTTP/2 server with HTTP/1 client request works with access logger enabled"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.ssl.enabled': true,
                "micronaut.server.http-version" : "2.0",
                'micronaut.ssl.buildSelfSigned': true,
                'micronaut.ssl.port': -1,
                "micronaut.http.client.log-level" : "TRACE",
                "micronaut.server.netty.log-level" : "TRACE",
                'micronaut.server.netty.access-logger.enabled': true
        ])
        RxHttpClient client = server.getApplicationContext().getBean(RxHttpClient)
        appender.events.clear()
        def result = client.retrieve("${server.URL}/http2").blockingFirst()

        expect:
        result == 'Version: HTTP_1_1'
        appender.headLog(10)

        cleanup:
        server.close()
    }

    private static class MemoryAppender extends AppenderBase<ILoggingEvent> {
        private final BlockingQueue<String> events = new LinkedBlockingQueue<>()

        @Override
        protected void append(ILoggingEvent e) {
            events.add(e.formattedMessage)
        }

        public Queue<String> getEvents() {
            return events
        }

        public String headLog(long timeout) {
            return events.poll(timeout, TimeUnit.SECONDS)
        }
    }


    @Client('/http2/sse')
    static interface TestHttp2Client {

        @Get(value = '/rich', processes = MediaType.TEXT_EVENT_STREAM)
        Flowable<Event<Person>> rich()
    }
}
