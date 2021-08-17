package io.micronaut.http.client


import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.reactivex.Single
import org.spockframework.runtime.IStandardStreamsListener
import org.spockframework.runtime.StandardStreamsCapturer
import spock.lang.Issue
import spock.lang.Specification

import javax.inject.Inject
import java.time.Duration
import java.util.concurrent.TimeUnit

@Issue('https://github.com/micronaut-projects/micronaut-core/issues/2971')
@MicronautTest
@Property(name = 'spec.name', value = 'ClientTimeoutSpec')
class ClientTimeoutSpec extends Specification {

    @Inject
    @Client('/')
    RxHttpClient client

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
        ex.message == 'Internal Server Error: The source did not signal an event for 1 seconds and has been terminated.'

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
    Single<String> get() {
        // Client will timeout in 2s (see TimeoutClientConfiguration below); we timeout the stream in 1s.
        client.get().timeout(1, TimeUnit.SECONDS)
    }
}

@Requires(property = 'spec.name', value = 'ClientTimeoutSpec')
@Requires(beans = TimeoutClientConfiguration)
@Client(value = 'http://www.google.com:81/', configuration = TimeoutClientConfiguration)
interface TimeoutClient {
    @Get
    Single<String> get();
}

@ConfigurationProperties(PREFIX)
class TimeoutClientConfiguration extends DefaultHttpClientConfiguration {
    @Override
    Optional<Duration> getConnectTimeout() {
        Optional.of(Duration.ofSeconds(2))
    }
}
