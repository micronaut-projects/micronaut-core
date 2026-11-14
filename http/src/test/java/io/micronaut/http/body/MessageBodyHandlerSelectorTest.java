package io.micronaut.http.body;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.codec.CodecException;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageBodyHandlerSelectorTest {

    private static final String SPEC = "MessageBodyHandlerSelectorTest";
    private static final MediaType FOO = MediaType.of("application/x-foo");

    @Test
    void defaultSelectionWithoutSelector() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC))) {
            MessageBodyHandlerRegistry registry = ctx.getBean(MessageBodyHandlerRegistry.class);
            assertInstanceOf(FirstHandler.class, registry.findWriter(Argument.of(Thing.class), List.of(FOO)).orElseThrow());
            assertInstanceOf(FirstHandler.class, registry.findReader(Argument.of(Thing.class), List.of(FOO)).orElseThrow());
        }
    }

    @Test
    void selectorChoosesAmongCandidates() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC, "selector.enabled", true))) {
            MessageBodyHandlerRegistry registry = ctx.getBean(MessageBodyHandlerRegistry.class);
            LastSelector selector = ctx.getBean(LastSelector.class);

            MessageBodyWriter<Thing> writer = registry.findWriter(Argument.of(Thing.class), List.of(FOO)).orElseThrow();
            assertInstanceOf(SecondHandler.class, writer);
            MessageBodyReader<Thing> reader = registry.findReader(Argument.of(Thing.class), List.of(FOO)).orElseThrow();
            assertInstanceOf(SecondHandler.class, reader);
            assertTrue(selector.seenCandidates.containsAll(List.of(FirstHandler.class, SecondHandler.class)));

            // the selection is cached per type and media types
            int calls = selector.calls;
            assertSame(writer, registry.findWriter(Argument.of(Thing.class), List.of(FOO)).orElseThrow());
            assertEquals(calls, selector.calls);
        }
    }

    @Test
    void selectorDeclinesToDefault() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC, "selector.enabled", true))) {
            MessageBodyHandlerRegistry registry = ctx.getBean(MessageBodyHandlerRegistry.class);
            ctx.getBean(LastSelector.class).decline = true;
            assertInstanceOf(FirstHandler.class, registry.findWriter(Argument.of(Thing.class), List.of(FOO)).orElseThrow());
            assertInstanceOf(FirstHandler.class, registry.findReader(Argument.of(Thing.class), List.of(FOO)).orElseThrow());
            assertFalse(registry.findWriter(Argument.of(Thing.class), List.of(MediaType.of("application/x-bar"))).isPresent());
        }
    }

    record Thing() {
    }

    abstract static class ThingHandler implements MessageBodyHandler<Thing> {
        @Override
        public boolean isReadable(Argument<Thing> type, @Nullable MediaType mediaType) {
            return FOO.equals(mediaType);
        }

        @Override
        public boolean isWriteable(Argument<Thing> type, @Nullable MediaType mediaType) {
            return FOO.equals(mediaType);
        }

        @Override
        public Thing read(Argument<Thing> type, @Nullable MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
            return new Thing();
        }

        @Override
        public Thing read(Argument<Thing> type, @Nullable MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            return new Thing();
        }

        @Override
        public void writeTo(Argument<Thing> type, MediaType mediaType, Thing object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    @Consumes("application/x-foo")
    @Produces("application/x-foo")
    static final class FirstHandler extends ThingHandler {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static final class SecondHandler extends ThingHandler {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    @Requires(property = "selector.enabled")
    static final class LastSelector implements MessageBodyHandlerSelector {
        final List<Class<?>> seenCandidates = new ArrayList<>();
        int calls;
        boolean decline;

        @Override
        public <T> @Nullable MessageBodyReader<T> selectReader(Argument<T> type, List<MediaType> mediaTypes, List<BeanRegistration<MessageBodyReader<T>>> candidates) {
            return select(type, candidates);
        }

        @Override
        public <T> @Nullable MessageBodyWriter<T> selectWriter(Argument<T> type, List<MediaType> mediaTypes, List<BeanRegistration<MessageBodyWriter<T>>> candidates) {
            return select(type, candidates);
        }

        private <H> @Nullable H select(Argument<?> type, List<BeanRegistration<H>> candidates) {
            if (type.getType() != Thing.class) {
                return null;
            }
            calls++;
            H last = null;
            for (BeanRegistration<H> candidate : candidates) {
                Class<?> beanType = candidate.getBeanDefinition().getBeanType();
                seenCandidates.add(beanType);
                if (ThingHandler.class.isAssignableFrom(beanType)) {
                    last = candidate.getBean();
                }
            }
            return decline ? null : last;
        }
    }
}
