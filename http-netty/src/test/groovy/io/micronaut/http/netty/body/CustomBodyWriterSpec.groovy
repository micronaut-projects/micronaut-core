package io.micronaut.http.netty.body

import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Order
import io.micronaut.core.type.Argument
import io.micronaut.core.type.MutableHeaders
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Produces
import io.micronaut.http.body.DefaultMessageBodyHandlerRegistry
import io.micronaut.http.body.MessageBodyWriter
import io.micronaut.http.codec.CodecException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

@MicronautTest
class CustomBodyWriterSpec extends Specification {

    @Inject
    DefaultMessageBodyHandlerRegistry bodyHandlerRegistry

    void "test message writer ordering works correctly"() {
        when:
        def writer = bodyHandlerRegistry.findWriter(Argument.of(A), List.of(MediaType.APPLICATION_JSON_TYPE))

        then:
        writer.isPresent()
        // ABodyWriter is chosen since it has higher order than JSONMessageHandler
        writer.get() instanceof ABodyWriter
    }

    void "test do not select un-assignable message body writer"() {
        when:
        def writer = bodyHandlerRegistry.findWriter(Argument.of(B), List.of(MediaType.APPLICATION_JSON_TYPE))

        then:
        writer.isPresent()
        // ABodyWriter is chosen since CBodyWriter cannot consume instances of B
        writer.get() instanceof ABodyWriter
    }

    void "test writer order works correctly for subtypes"() {
        when:
        def writer = bodyHandlerRegistry.findWriter(Argument.of(C), List.of(MediaType.APPLICATION_JSON_TYPE))

        then:
        writer.isPresent()
        // CBodyWriter is chosen since it has the highest order
        writer.get() instanceof CBodyWriter
    }

    void "test writer is selected if it has generic type argument"() {
        when:
        def writer = bodyHandlerRegistry.findWriter(Argument.of(K), List.of(MediaType.APPLICATION_JSON_TYPE))

        then:
        writer.isPresent()
        // NettyJsonHandler allows any type, since the argument is generic, so it is chosen in this case
        writer.get() instanceof NettyJsonHandler
    }

    static class A {
        String myHeader
    }

    static class B extends A {}

    static class C extends B {
        String anotherHeader
    }

    static class K {}

    @Singleton
    @Produces(MediaType.APPLICATION_JSON)
    // Higher than NettyJsonHandler
    @Order(1)
    static class ABodyWriter implements MessageBodyWriter<A> {

        @Override
        void writeTo(@NonNull Argument<A> type, @NonNull MediaType mediaType, A object, @NonNull MutableHeaders outgoingHeaders, @NonNull OutputStream outputStream) throws CodecException {
            outgoingHeaders.add("my-header", object.myHeader)
        }
    }

    @Singleton
    @Produces(MediaType.APPLICATION_JSON)
    // Higher than ABodyWriter
    @Order(2)
    static class CBodyWriter implements MessageBodyWriter<C> {

        @Override
        void writeTo(@NonNull Argument<C> type, @NonNull MediaType mediaType, C object, @NonNull MutableHeaders outgoingHeaders, @NonNull OutputStream outputStream) throws CodecException {
            outgoingHeaders.add("my-header", object.myHeader).add("another-header", object.anotherHeader)
        }
    }

}
