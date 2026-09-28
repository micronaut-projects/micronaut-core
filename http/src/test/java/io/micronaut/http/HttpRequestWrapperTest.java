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
    void aSubclassThatDoesNotOverrideTheBodyKeepsItWithoutDecodingIt() {
        FreshBodyRequest request = new FreshBodyRequest();
        HeadersOnlyWrapper wrapper = new HeadersOnlyWrapper(request);

        assertFalse(HttpRequestWrapper.replacesBody(wrapper));
        assertEquals(0, request.decoded);
        // looked up once per class: the same again
        assertFalse(HttpRequestWrapper.replacesBody(new HeadersOnlyWrapper(request)));
        assertEquals(0, request.decoded);
    }

    @Test
    void aSubclassThatDoesNotOverrideTheBodyDoesNotConsumeTheBody() {
        ConsumingBodyRequest request = new ConsumingBodyRequest();

        assertFalse(HttpRequestWrapper.replacesBody(new HeadersOnlyWrapper(request)));
        assertEquals(0, request.consumed);
        // the body is still there to be read
        assertEquals(Optional.of("body"), request.getBody());
    }

    @Test
    void anAnonymousSubclassThatDoesNotOverrideTheBodyKeepsIt() {
        FreshBodyRequest request = new FreshBodyRequest();
        HttpRequestWrapper<Object> wrapper = new HttpRequestWrapper<>(request) {
            @Override
            public String getPath() {
                return "/other";
            }
        };

        assertFalse(HttpRequestWrapper.replacesBody(wrapper));
        assertEquals(0, request.decoded);
    }

    @Test
    void aSubclassOfTheMutableWrapperThatDoesNotOverrideTheBodyKeepsItUntilItsBodyIsSet() {
        FreshBodyRequest request = new FreshBodyRequest();
        MutableHttpRequestWrapper<Object> wrapper = new MutableHttpRequestWrapper<>(ConversionService.SHARED, request) {
            @Override
            public MutableHttpHeaders getHeaders() {
                return super.getHeaders();
            }
        };

        assertFalse(HttpRequestWrapper.replacesBody(wrapper));
        assertEquals(0, request.decoded);

        wrapper.body("set");

        assertTrue(HttpRequestWrapper.replacesBody(wrapper));
    }

    @Test
    void aSubclassOfASubclassThatOverridesTheBodyReplacesIt() {
        FreshBodyRequest request = new FreshBodyRequest();
        HttpRequestWrapper<Object> sanitized = new SanitizingWrapper(request) {
            @Override
            public String getPath() {
                return "/other";
            }
        };

        assertTrue(HttpRequestWrapper.replacesBody(sanitized));
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
     * A request whose body is decoded from a stream, which the first call consumes, like a
     * servlet request.
     */
    static final class ConsumingBodyRequest extends SimpleHttpRequest<Object> {
        int consumed;

        ConsumingBodyRequest() {
            super(HttpMethod.POST, "/items", null);
        }

        @Override
        public Optional<Object> getBody() {
            if (consumed++ > 0) {
                return Optional.empty();
            }
            return Optional.of("body");
        }
    }

    /**
     * A wrapper of a third party that only changes the headers.
     */
    static class HeadersOnlyWrapper extends HttpRequestWrapper<Object> {
        HeadersOnlyWrapper(HttpRequest<Object> delegate) {
            super(delegate);
        }

        @Override
        public HttpHeaders getHeaders() {
            return super.getHeaders();
        }
    }

    /**
     * A wrapper of a third party that replaces the body.
     */
    static class SanitizingWrapper extends HttpRequestWrapper<Object> {
        SanitizingWrapper(HttpRequest<Object> delegate) {
            super(delegate);
        }

        @Override
        public Optional<Object> getBody() {
            return Optional.of("sanitized");
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
