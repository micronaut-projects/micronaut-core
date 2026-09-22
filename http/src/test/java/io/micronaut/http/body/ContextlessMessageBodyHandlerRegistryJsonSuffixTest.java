package io.micronaut.http.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.body.JsonMessageHandler;
import io.micronaut.runtime.ApplicationConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class ContextlessMessageBodyHandlerRegistryJsonSuffixTest {

    private static final Argument<Map<String, Object>> MAP = Argument.mapOf(String.class, Object.class);
    private static final MediaType ACME_JSON = MediaType.of("application/vnd.acme+json");
    private static final MediaType CUSTOM_JSON = MediaType.of("application/vnd.custom+json");

    @Test
    void jsonHandlerReadsAndWritesJsonSuffixMediaTypes() {
        ContextlessMessageBodyHandlerRegistry registry = registry();

        for (String mediaType : List.of("application/vnd.acme+json", "application/prs.hal-forms+json", "application/vnd.collection+json", "application/alps+json", "application/VND.UPPER+JSON")) {
            List<MediaType> mediaTypes = List.of(MediaType.of(mediaType));
            assertInstanceOf(JsonMessageHandler.class, registry.findWriter(MAP, mediaTypes).orElseThrow(), mediaType);
            assertInstanceOf(JsonMessageHandler.class, registry.findReader(MAP, mediaTypes).orElseThrow(), mediaType);
        }
    }

    @Test
    void mediaTypesWithoutJsonSuffixAreNotJson() {
        ContextlessMessageBodyHandlerRegistry registry = registry();

        for (String mediaType : List.of("application/xml", "application/vnd.acme+xml", "application/json+feed", "application/x-json-stream")) {
            List<MediaType> mediaTypes = List.of(MediaType.of(mediaType));
            assertFalse(registry.findWriter(MAP, mediaTypes).isPresent(), mediaType);
            assertFalse(registry.findReader(MAP, mediaTypes).isPresent(), mediaType);
        }
    }

    @Test
    void handlerForExactJsonSuffixMediaTypeWins() {
        ContextlessMessageBodyHandlerRegistry registry = registry();
        CustomHandler custom = new CustomHandler();
        registry.add(CUSTOM_JSON, custom);

        assertSame(custom, registry.findWriter(MAP, List.of(CUSTOM_JSON)).orElseThrow());
        assertSame(custom, registry.findReader(MAP, List.of(CUSTOM_JSON)).orElseThrow());
        assertInstanceOf(JsonMessageHandler.class, registry.findWriter(MAP, List.of(ACME_JSON)).orElseThrow());
    }

    private static ContextlessMessageBodyHandlerRegistry registry() {
        ContextlessMessageBodyHandlerRegistry registry = new ContextlessMessageBodyHandlerRegistry(new ApplicationConfiguration(), ByteArrayBufferFactory.INSTANCE);
        registry.add(MediaType.APPLICATION_JSON_TYPE, new JsonMessageHandler<>(JsonMapper.createDefault()));
        return registry;
    }

    private static final class CustomHandler implements MessageBodyHandler<Object> {
        @Override
        public Object read(Argument<Object> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeTo(Argument<Object> type, MediaType mediaType, Object object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            throw new UnsupportedOperationException();
        }
    }
}
