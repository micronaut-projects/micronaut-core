package io.micronaut.http.filter;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpRequestWrapper;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.simple.SimpleHttpRequest;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The binding of a {@link Body} argument of a filter method, through the wrappers an earlier
 * filter continued with.
 */
class FilterBodyBinderTest {

    private static final ByteBodyFactory BODIES = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    @Test
    void theBytesOfAServerRequestAreBound() {
        assertEquals("bytes", bind(String.class, server("bytes")).orElseThrow());
        assertArrayEquals("bytes".getBytes(StandardCharsets.UTF_8), bind(byte[].class, server("bytes")).orElseThrow());
        assertEquals("bytes", bind(ByteBuffer.class, server("bytes")).orElseThrow().toString(StandardCharsets.UTF_8));
    }

    @Test
    void aTypeTheBytesAreNotBoundAsIsUnsatisfied() {
        assertFalse(bind(Integer.class, server("1")).isPresent());
    }

    @Test
    void theBytesAreBoundThroughAWrapper() {
        assertEquals("bytes", bind(String.class, new HttpRequestWrapper<>(server("bytes"))).orElseThrow());
    }

    @Test
    void theBytesAreBoundThroughNestedWrappers() {
        HttpRequest<Object> nested = new HttpRequestWrapper<>(new HttpRequestWrapper<>(server("bytes")));

        assertEquals("bytes", bind(String.class, nested).orElseThrow());
    }

    @Test
    void theBytesAreBoundThroughAWrapperThatKeptTheBody() {
        assertEquals("bytes", bind(String.class, new BodyWrapper(server("bytes"), () -> null)).orElseThrow());
    }

    @Test
    void theBytesAreBoundThroughAMutableCopyWhoseBodyWasNotSet() {
        assertEquals("bytes", bind(String.class, MutableServerRequest.of(server("bytes"))).orElseThrow());
    }

    @Test
    void theBodySetOnAMutableCopyIsBound() {
        MutableHttpRequest<?> copy = MutableServerRequest.of(server("bytes"));
        copy.body("replaced");

        assertEquals("replaced", bind(String.class, copy).orElseThrow());
        assertEquals("replaced", bind(String.class, new HttpRequestWrapper<>(cast(copy))).orElseThrow());
    }

    @Test
    void theBodySetOnAMutableWrapperIsBound() {
        MutableHttpRequest<?> wrapper = MutableHttpRequestWrapper.wrapIfNecessary(ConversionService.SHARED, new HttpRequestWrapper<>(server("bytes")));
        wrapper.body("replaced");

        assertEquals("replaced", bind(String.class, wrapper).orElseThrow());
    }

    @Test
    void theBodyOfAWrapperThatReplacedItIsConvertedToTheArgumentType() {
        HttpRequest<?> wrapper = new BodyWrapper(server("bytes"), () -> "42");

        assertEquals(42, bind(Integer.class, wrapper).orElseThrow());
        assertEquals("42", bind(String.class, wrapper).orElseThrow());
    }

    @Test
    void aBodyThatCannotBeConvertedIsUnsatisfied() {
        HttpRequest<?> wrapper = new BodyWrapper(server("bytes"), () -> "not a number");

        assertFalse(bind(Integer.class, wrapper).isPresent());
    }

    @Test
    void aRequestWithoutABodyIsUnsatisfied() {
        assertFalse(bind(String.class, new SimpleHttpRequest<>(HttpMethod.POST, "/", null)).isPresent());
        assertFalse(bind(String.class, new HttpRequestWrapper<>(new SimpleHttpRequest<>(HttpMethod.POST, "/", null))).isPresent());
    }

    @Test
    void theBodyOfARequestThatIsNotAServerRequestIsBound() {
        assertEquals("plain", bind(String.class, new SimpleHttpRequest<>(HttpMethod.POST, "/", "plain")).orElseThrow());
    }

    @SuppressWarnings("unchecked")
    private static <T> Optional<T> bind(Class<T> type, HttpRequest<?> request) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(Body.class.getName(), Collections.emptyMap());
        Argument<T> argument = Argument.of(type, "body", metadata);
        ArgumentBinder<T, HttpRequest<?>> binder = registry().findArgumentBinder(argument).orElseThrow();
        ArgumentConversionContext<T> context = ConversionContext.of(argument);
        ArgumentBinder.BindingResult<T> result = ((BaseFilterProcessor.AsyncBodyBinder<T>) binder).bindAsync(context, request).tryCompleteValue();
        return result.isSatisfied() ? result.getValue() : Optional.empty();
    }

    private static RequestBinderRegistry registry() {
        BaseFilterProcessor<ServerFilter> processor = new BaseFilterProcessor<>(null, ServerFilter.class) {
            @Override
            protected void addFilter(Supplier<GenericHttpFilter> factory, AnnotationMetadata methodAnnotations, FilterMetadata metadata) {
                // not used
            }
        };
        try {
            Field field = BaseFilterProcessor.class.getDeclaredField("argumentBinderRegistry");
            field.setAccessible(true);
            return (RequestBinderRegistry) field.get(processor);
        } catch (ReflectiveOperationException e) {
            throw new LinkageError(e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static HttpRequest<Object> cast(HttpRequest<?> request) {
        return (HttpRequest<Object>) request;
    }

    private static ServerRequest server(String body) {
        return new ServerRequest(body);
    }

    /**
     * A server request with the given bytes, that is not mutable.
     */
    private static final class ServerRequest extends HttpRequestWrapper<Object> implements ServerHttpRequest<Object> {
        private final ByteBody bytes;

        ServerRequest(String body) {
            super(new SimpleHttpRequest<>(HttpMethod.POST, "/", null));
            this.bytes = BODIES.copyOf(body, StandardCharsets.UTF_8);
        }

        @Override
        public ByteBody byteBody() {
            return bytes;
        }

        @Override
        public ByteBodyFactory byteBodyFactory() {
            return BODIES;
        }
    }

    /**
     * A wrapper overriding the body.
     */
    private static final class BodyWrapper extends HttpRequestWrapper<Object> {
        private final Supplier<Object> body;

        BodyWrapper(HttpRequest<?> delegate, Supplier<Object> body) {
            super(cast(delegate));
            this.body = body;
        }

        @Override
        public Optional<Object> getBody() {
            return Optional.ofNullable(body.get());
        }
    }
}
