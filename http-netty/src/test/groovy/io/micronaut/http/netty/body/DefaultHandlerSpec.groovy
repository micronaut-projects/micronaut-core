package io.micronaut.http.netty.body

import io.micronaut.core.annotation.NextMajorVersion
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.type.Argument
import io.micronaut.core.type.Headers
import io.micronaut.core.type.MutableHeaders
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Produces
import io.micronaut.http.body.CharSequenceBodyWriter
import io.micronaut.http.body.DefaultMessageBodyHandlerRegistry
import io.micronaut.http.body.MessageBodyHandler
import io.micronaut.http.body.StringBodyReader
import io.micronaut.http.body.TextPlainObjectBodyReader
import io.micronaut.http.codec.CodecException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.PendingFeature
import spock.lang.Specification
import spock.lang.Unroll

@MicronautTest
class DefaultHandlerSpec extends Specification {

    @Inject
    DefaultMessageBodyHandlerRegistry bodyHandlerRegistry

    void "test default writer / reader for ALL type"() {
        when:
            def writer = bodyHandlerRegistry.findWriter(Argument.listOf(SomeBean), MediaType.ALL_TYPE)

        then:
            writer.isPresent()
            writer.get() instanceof NettyJsonHandler

        when:
            def reader = bodyHandlerRegistry.findReader(Argument.listOf(SomeBean), MediaType.ALL_TYPE)

        then:
            reader.isPresent()
            reader.get() instanceof NettyJsonHandler
    }

    void "test default writer / reader for missing type"() {
        when:
            def writer = bodyHandlerRegistry.findWriter(Argument.listOf(SomeBean))

        then:
            writer.isEmpty()

        when:
            def reader = bodyHandlerRegistry.findReader(Argument.listOf(SomeBean))

        then:
            reader.isEmpty()
    }

    @Unroll
    void "test string body handlers"() {
        when:
            def writer = bodyHandlerRegistry.findWriter(Argument.STRING, List.of(mediaType))

        then:
            writer.isPresent()
            writer.get() instanceof CharSequenceBodyWriter

        when:
            def reader = bodyHandlerRegistry.findReader(Argument.STRING, List.of(mediaType))

        then:
            reader.isPresent()
            reader.get() instanceof StringBodyReader

        where:
            mediaType << [
                    MediaType.APPLICATION_ATOM_XML_TYPE,
                    MediaType.APPLICATION_VND_ERROR_TYPE,
                    MediaType.APPLICATION_GRAPHQL_TYPE,
                    MediaType.APPLICATION_JSON_FEED_TYPE,
                    MediaType.APPLICATION_JSON_GITHUB_TYPE,
                    MediaType.APPLICATION_JSON_TYPE,
                    MediaType.TEXT_CSS_TYPE,
                    MediaType.TEXT_PLAIN_TYPE
            ]
    }

    void "test object text plain writer"() {
        when:
            def writer = bodyHandlerRegistry.findWriter(Argument.OBJECT_ARGUMENT, List.of(MediaType.TEXT_PLAIN_TYPE))

        then:
            writer.isEmpty()

    }

    void "test object text plain reader"() {
        when:
            def reader = bodyHandlerRegistry.findReader(Argument.OBJECT_ARGUMENT, List.of(MediaType.TEXT_PLAIN_TYPE))

        then:
            reader.isPresent()
            reader.get() instanceof TextPlainObjectBodyReader
    }

    @NextMajorVersion("Avoid writing / reading non textual content type")
    @PendingFeature(reason = "Non textual should be avoided")
    void "test not body handlers"() {
        when:
            def writer = bodyHandlerRegistry.findWriter(Argument.STRING, List.of(mediaType))

        then:
            writer.isEmpty()

        when:
            def reader = bodyHandlerRegistry.findReader(Argument.STRING, List.of(mediaType))

        then:
            reader.isEmpty()

        where:
            mediaType << [
                    MediaType.APPLICATION_OCTET_STREAM_TYPE,
                    MediaType.APPLICATION_PDF_TYPE,
                    MediaType.MICROSOFT_EXCEL_TYPE
            ]
    }

    @Singleton
    @Produces(MediaType.APPLICATION_ATOM_XML)
    @Consumes(MediaType.APPLICATION_ATOM_XML)
    static class CustomAtomXmlHandler implements MessageBodyHandler<Object> {

        @Override
        String read(@NonNull Argument<Object> type, @Nullable MediaType mediaType, @NonNull Headers httpHeaders, @NonNull InputStream inputStream) throws CodecException {
            return null
        }

        @Override
        void writeTo(@NonNull Argument<Object> type, @NonNull MediaType mediaType, Object object, @NonNull MutableHeaders outgoingHeaders, @NonNull OutputStream outputStream) throws CodecException {

        }
    }

    class SomeBean {}

}
