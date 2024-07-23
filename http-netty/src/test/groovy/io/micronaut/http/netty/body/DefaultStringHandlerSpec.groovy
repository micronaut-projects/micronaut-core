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
import io.micronaut.http.body.DefaultMessageBodyHandlerRegistry
import io.micronaut.http.body.MessageBodyHandler
import io.micronaut.http.body.StringBodyReader
import io.micronaut.http.codec.CodecException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.PendingFeature
import spock.lang.Specification
import spock.lang.Unroll

@MicronautTest
class DefaultStringHandlerSpec extends Specification {

    @Inject
    DefaultMessageBodyHandlerRegistry bodyHandlerRegistry

    @Unroll
    void "test string body handlers"() {
        when:
            def writer = bodyHandlerRegistry.findWriter(Argument.STRING, List.of(mediaType))

        then:
            writer.isPresent()
            writer.get() instanceof NettyCharSequenceBodyWriter

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

}
