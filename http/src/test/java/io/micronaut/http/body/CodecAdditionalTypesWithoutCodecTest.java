package io.micronaut.http.body;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.json.body.JsonMessageHandler;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code micronaut.codec.json.additional-types} selects the JSON handler without a
 * {@link MediaTypeCodec} bean named {@code json}.
 */
class CodecAdditionalTypesWithoutCodecTest {

    private static final String SPEC_NAME = "CodecAdditionalTypesWithoutCodecTest";
    private static final Argument<Map<String, Object>> MAP = Argument.mapOf(String.class, Object.class);
    private static final List<MediaType> ACME_JSON = List.of(MediaType.of("application/vnd.acme+json"));
    private static final List<MediaType> OTHER_JSON = List.of(MediaType.of("application/vnd.other+json"));

    @Test
    void additionalTypesSelectTheJsonHandler() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.codec.json.additional-types", List.of("application/vnd.acme+json")
        ))) {
            assertTrue(context.findBean(MediaTypeCodec.class, Qualifiers.byName("json")).isEmpty());
            MessageBodyHandlerRegistry registry = context.getBean(MessageBodyHandlerRegistry.class);

            assertInstanceOf(JsonMessageHandler.class, registry.findWriter(MAP, ACME_JSON).orElseThrow());
            assertInstanceOf(JsonMessageHandler.class, registry.findReader(MAP, ACME_JSON).orElseThrow());
            assertFalse(registry.findWriter(MAP, OTHER_JSON).isPresent());
            assertFalse(registry.findReader(MAP, OTHER_JSON).isPresent());
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Replaces(io.micronaut.json.codec.JsonMediaTypeCodec.class)
    @Named("not-json-core")
    @Singleton
    static class NoJsonCoreCodec extends UnusedCodec {
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Replaces(io.micronaut.jackson.codec.JsonMediaTypeCodec.class)
    @Named("not-jackson")
    @Singleton
    static class NoJacksonCodec extends UnusedCodec {
    }

    abstract static class UnusedCodec implements MediaTypeCodec {
        @Override
        public Collection<MediaType> getMediaTypes() {
            return List.of();
        }

        @Override
        public <T> T decode(Argument<T> type, InputStream inputStream) throws CodecException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> void encode(T object, OutputStream outputStream) throws CodecException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> byte[] encode(T object) throws CodecException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T, B> ByteBuffer<B> encode(T object, ByteBufferFactory<?, B> allocator) throws CodecException {
            throw new UnsupportedOperationException();
        }
    }
}
