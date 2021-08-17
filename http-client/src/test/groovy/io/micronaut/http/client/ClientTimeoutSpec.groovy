package io.micronaut.http.client


import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import org.spockframework.runtime.IStandardStreamsListener
import org.spockframework.runtime.StandardStreamsCapturer
import reactor.core.publisher.Mono
import spock.lang.Issue
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.TimeUnit

@Issue('https://github.com/micronaut-projects/micronaut-core/issues/2971')
@MicronautTest
@Property(name = 'spec.name', value = 'ClientTimeoutSpec')
class ClientTimeoutSpec extends Specification {

    @Inject
    @Client('/')
    HttpClient client

    def 'test that UndeliverableException is not output to console'() {
        setup:
        def listener = new UndeliverableExceptionListener()
        def capturer = new StandardStreamsCapturer()
        capturer.addStandardStreamsListener(listener)
        capturer.start()

        when:
        client.toBlocking().retrieve('/')

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get()._embedded.errors[0].message.contains('Internal Server Error: Did not observe any item or terminal signal within 1000ms')

        and: 'wait for System.err messages to come in'
        Thread.sleep(2000)
        !listener.undeliverable

        cleanup:
        capturer.stop()
        capturer.removeStandardStreamsListener(listener)
    }
}

class UndeliverableExceptionListener implements IStandardStreamsListener {
    boolean undeliverable = false

    @Override
    void standardOut(String message) { }

    @Override
    void standardErr(String message) {
        undeliverable = undeliverable || message.contains('io.reactivex.exceptions.UndeliverableException')
    }
}

@Requires(property = 'spec.name', value = 'ClientTimeoutSpec')
@Controller('/')
class TimeoutController {
    @Inject
    TimeoutClient client

    @Get
    @SingleResult
    Publisher<String> get() {
        // Client will timeout in 2s (see TimeoutClientConfiguration below); we timeout the stream in 1s.
        Mono.from(client.get()).timeout(Duration.ofSeconds(1))
    }
}

@Requires(property = 'spec.name', value = 'ClientTimeoutSpec')
@Requires(beans = TimeoutClientConfiguration)
@Client(value = 'http://www.google.com:81/', configuration = TimeoutClientConfiguration)
interface TimeoutClient {
    @Get
    Publisher<String> get();
}

@ConfigurationProperties(PREFIX)
class TimeoutClientConfiguration extends DefaultHttpClientConfiguration {
    @Override
    Optional<Duration> getConnectTimeout() {
        Optional.of(Duration.ofSeconds(2))
    }
}
