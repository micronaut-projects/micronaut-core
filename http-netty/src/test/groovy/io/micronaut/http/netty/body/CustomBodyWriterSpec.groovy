package io.micronaut.http.netty.body

import io.micronaut.core.annotation.NonNull
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
        writer.get() instanceof ABodyWriter
    }

    class A {
        String myHeader
    }

    @Singleton
    @Produces(MediaType.APPLICATION_JSON)
    static class ABodyWriter implements MessageBodyWriter<A> {

        // Higher than NettyJsonHandler
        int order = 1

        @Override
        void writeTo(@NonNull Argument<A> type, @NonNull MediaType mediaType, A object, @NonNull MutableHeaders outgoingHeaders, @NonNull OutputStream outputStream) throws CodecException {
            outgoingHeaders.add("my-header", object.myHeader)
        }
    }

}
