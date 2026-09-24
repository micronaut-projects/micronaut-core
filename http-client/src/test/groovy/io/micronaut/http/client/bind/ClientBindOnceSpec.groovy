package io.micronaut.http.client.bind

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Status
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.util.concurrent.atomic.AtomicInteger

@Property(name = 'spec.name', value = 'ClientBindOnceSpec')
@MicronautTest
class ClientBindOnceSpec extends Specification {

    @Inject CountingClient client
    @Inject CountingBinder binder

    void setup() {
        binder.reset()
    }

    void "argument binders run once for a Mono<Void> client method"() {
        when:
        client.mono("abc").block()

        then:
        binder.count.get() == 1
        binder.lastValue == "abc"
    }

    void "argument binders run once for a client method returning a value"() {
        when:
        String result = client.value("hello").block()

        then:
        result == "hello"
        binder.count.get() == 1
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @Bindable
    static @interface CountingHeader {
    }

    @Requires(property = 'spec.name', value = 'ClientBindOnceSpec')
    @Singleton
    static class CountingBinder implements AnnotatedClientArgumentRequestBinder<CountingHeader> {

        final AtomicInteger count = new AtomicInteger()
        volatile String lastValue

        @Override
        Class<CountingHeader> getAnnotationType() {
            CountingHeader
        }

        @Override
        void bind(ArgumentConversionContext<Object> context, ClientRequestUriContext uriContext, Object value, MutableHttpRequest<?> request) {
            count.incrementAndGet()
            lastValue = value.toString()
            request.header("X-Count", value.toString())
        }

        void reset() {
            count.set(0)
            lastValue = null
        }
    }

    @Requires(property = 'spec.name', value = 'ClientBindOnceSpec')
    @Client("/bind-once")
    static interface CountingClient {

        @Get("/void")
        Mono<Void> mono(@CountingHeader String value)

        @Get("/value")
        Mono<String> value(@CountingHeader String value)
    }

    @Requires(property = 'spec.name', value = 'ClientBindOnceSpec')
    @Controller("/bind-once")
    static class CountingController {

        @Get("/void")
        @Status(HttpStatus.OK)
        void noBody() {
        }

        @Get("/value")
        String value(HttpRequest<?> request) {
            request.headers.get("X-Count")
        }
    }
}
