package io.micronaut.http;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.simple.SimpleHttpRequest;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether a wrapper replaced the body of the request it wraps, on a request that decodes a new
 * body on each call, like a server that decodes the body of a request lazily.
 */
class HttpRequestWrapperTest {

    @Test
    void aPlainWrapperKeepsTheBodyWithoutDecodingIt() {
        FreshBodyRequest request = new FreshBodyRequest();

        assertFalse(HttpRequestWrapper.replacesBody(new HttpRequestWrapper<>(request)));
        assertEquals(0, request.decoded);
    }

    @Test
    void aWrapperMarkedToKeepTheBodyKeepsItWithoutDecodingIt() {
        FreshBodyRequest request = new FreshBodyRequest();

        assertFalse(HttpRequestWrapper.replacesBody(new HidingView(request)));
        assertEquals(0, request.decoded);
    }

    @Test
    void aMutableWrapperKeepsTheBodyUntilItsBodyIsSet() {
        FreshBodyRequest request = new FreshBodyRequest();
        MutableHttpRequest<?> mutable = MutableHttpRequestWrapper.wrapIfNecessary(ConversionService.SHARED, new HttpRequestWrapper<>(request));

        assertFalse(HttpRequestWrapper.replacesBody(mutable));
        assertEquals(0, request.decoded);

        mutable.body("set");

        assertTrue(HttpRequestWrapper.replacesBody(mutable));
    }

    @Test
    void aWrapperThatReturnsAnotherBodyReplacesIt() {
        FreshBodyRequest request = new FreshBodyRequest();
        HttpRequestWrapper<Object> sanitized = new HttpRequestWrapper<>(request) {
            @Override
            public Optional<Object> getBody() {
                return Optional.of("sanitized");
            }
        };

        assertTrue(HttpRequestWrapper.replacesBody(sanitized));
    }

    @Test
    void aWrapperThatReturnsTheBodyOfTheRequestKeepsIt() {
        SimpleHttpRequest<Object> request = new SimpleHttpRequest<>(HttpMethod.POST, "/items", "body");
        HttpRequestWrapper<Object> same = new HttpRequestWrapper<>(request) {
            @Override
            public Optional<Object> getBody() {
                return getDelegate().getBody();
            }
        };

        assertFalse(HttpRequestWrapper.replacesBody(same));
    }

    @Test
    void aRequestThatIsNotAWrapperReplacesNothing() {
        FreshBodyRequest request = new FreshBodyRequest();

        assertFalse(HttpRequestWrapper.replacesBody(request));
        assertEquals(0, request.decoded);
    }

    /**
     * A request that decodes a new body on each call.
     */
    static final class FreshBodyRequest extends SimpleHttpRequest<Object> {
        int decoded;

        FreshBodyRequest() {
            super(HttpMethod.POST, "/items", null);
        }

        @Override
        public Optional<Object> getBody() {
            decoded++;
            return Optional.of(new Object());
        }
    }

    /**
     * A view that hides a decoded body so that it is read again from the bytes.
     */
    private static final class HidingView extends HttpRequestWrapper<Object> implements BodyPreservingRequestWrapper {
        HidingView(HttpRequest<Object> delegate) {
            super(delegate);
        }

        @Override
        public Optional<Object> getBody() {
            return Optional.empty();
        }
    }
}
