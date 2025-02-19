package io.micronaut.http.netty.body

import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.annotation.Order
import io.micronaut.core.type.Argument
import io.micronaut.core.type.Headers
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.body.DefaultMessageBodyHandlerRegistry
import io.micronaut.http.body.MessageBodyReader
import io.micronaut.http.codec.CodecException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

@MicronautTest
class CustomBodyReaderSpec extends Specification {

    @Inject
    DefaultMessageBodyHandlerRegistry bodyHandlerRegistry

    void "test message reader ordering works correctly"() {
        when:
        def reader = bodyHandlerRegistry.findReader(Argument.of(A), List.of(MediaType.APPLICATION_JSON_TYPE))

        then:
        // The implementation with highest order is chosen out of all: ABodyReader
        reader.isPresent()
        reader.get() instanceof ABodyReader
    }

    void "test do not select un-assignable message body reader"() {
        when:
        def reader = bodyHandlerRegistry.findReader(Argument.of(B), List.of(MediaType.APPLICATION_JSON_TYPE))

        then:
        reader.isPresent()
        // ABodyReader can only create instances of class A, so we use the CBodyReader
        reader.get() instanceof CBodyReader
    }

    void "test reader order works correctly for subtypes"() {
        when:
        def reader = bodyHandlerRegistry.findReader(Argument.of(C), List.of(MediaType.APPLICATION_JSON_TYPE))

        then:
        reader.isPresent()
        // ABodyReader can only create instances of class A, so we use the CBodyReader
        reader.get() instanceof CBodyReader
    }

    void "test custom my string reader is found"() {
        when:
        def reader = bodyHandlerRegistry.findReader(Argument.STRING, List.of(MediaType.of("my/string")))

        then:
        reader.isPresent()
        reader.get() instanceof MyStringTypeReader
    }

    static class A {
        String myHeader
    }

    static class B extends A {}

    static class C extends B {
        String anotherHeader
    }

    @Singleton
    @Consumes(MediaType.APPLICATION_JSON)
    // Higher than NettyJsonHandler
    @Order(-1)
    static class ABodyReader implements MessageBodyReader<A> {

        @Override
        A read(@NonNull Argument<A> type, @Nullable MediaType mediaType, @NonNull Headers httpHeaders, @NonNull InputStream inputStream) throws CodecException {
            return new A(String.valueOf(httpHeaders.get("my-header")))
        }
    }

    @Singleton
    @Consumes(MediaType.APPLICATION_JSON)
    // Higher than ABodyWriter
    @Order(-2)
    static class CBodyReader implements MessageBodyReader<C> {

        @Override
        C read(@NonNull Argument<C> type, @Nullable MediaType mediaType, @NonNull Headers httpHeaders, @NonNull InputStream inputStream) throws CodecException {
            return new C(String.valueOf(httpHeaders.get("my-header"), String.valueOf(httpHeaders.get("another-header"))))
        }
    }

    @Singleton
    @Consumes("my/string")
    static class MyStringTypeReader implements MessageBodyReader<String> {


        @Override
        String read(@NonNull Argument<String> type, @Nullable MediaType mediaType, @NonNull Headers httpHeaders, @NonNull InputStream inputStream) throws CodecException {
            return "ABC"
        }
    }

}
