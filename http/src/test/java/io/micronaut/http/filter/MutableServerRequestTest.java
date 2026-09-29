package io.micronaut.http.filter;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.DirectByteBodyAccess;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.simple.SimpleHttpRequest;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mutable request a filter method is given for a request that is not mutable.
 */
class MutableServerRequestTest {

    private static final ByteBodyFactory BODIES = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    private static final InetSocketAddress REMOTE = InetSocketAddress.createUnresolved("client.example", 4000);
    private static final InetSocketAddress LOCAL = InetSocketAddress.createUnresolved("server.example", 8080);

    @Test
    void aMutableRequestIsGivenAsItIs() {
        SimpleHttpRequest<String> request = new SimpleHttpRequest<>(HttpMethod.GET, "/items", null);

        assertSame(request, MutableServerRequest.of(request));
    }

    @Test
    void aServerRequestIsGivenAMutableCopyThatIsAServerRequest() {
        ServerRequest request = new ServerRequest(new SimpleHttpRequest<>(HttpMethod.POST, "/items?a=1", null), "body");

        MutableHttpRequest<?> mutable = MutableServerRequest.of(request);

        assertInstanceOf(MutableServerRequest.class, mutable);
        ServerHttpRequest<?> server = assertInstanceOf(ServerHttpRequest.class, mutable);
        assertSame(request, ((MutableServerRequest<?>) mutable).request());
        // the connection and the bytes of the body are those of the request
        assertSame(request.byteBody(), server.byteBody());
        assertSame(BODIES, server.byteBodyFactory());
        assertEquals(HttpVersion.HTTP_2_0, mutable.getHttpVersion());
        assertSame(REMOTE, mutable.getRemoteAddress());
        assertSame(LOCAL, mutable.getServerAddress());
        assertEquals("server.example", mutable.getServerName());
        assertTrue(mutable.isSecure());
        assertTrue(mutable.getSslSession().isEmpty());
        assertTrue(mutable.getCertificate().isEmpty());
        // the copy is already mutable, and changes stay in it
        assertSame(mutable, mutable.mutate());
    }

    @Test
    void theCopyHasItsOwnUriParametersAndBody() {
        ServerRequest request = new ServerRequest(new SimpleHttpRequest<>(HttpMethod.POST, "/items?a=1", null), "body");
        MutableHttpRequest<?> mutable = MutableServerRequest.of(request);

        assertSame(mutable, mutable.uri(URI.create("/v2/items?b=2")));
        mutable.getParameters().add("c", "3");
        mutable.getHeaders().add("X-Copy", "yes");
        mutable.cookie(Cookie.of("session", "abc"));
        mutable.setConversionService(ConversionService.SHARED);

        assertEquals("/v2/items?b=2", mutable.getUri().toString());
        assertEquals("/items?a=1", request.getUri().toString());
        assertEquals("POST /v2/items?b=2", mutable.toString());
        assertEquals("yes", mutable.getHeaders().get("X-Copy"));
        assertEquals("3", mutable.getParameters().get("c"));
    }

    @Test
    void theBytesOfTheRequestAreTheBodyUntilTheBodyOfTheCopyIsSet() {
        ServerRequest request = new ServerRequest(new SimpleHttpRequest<>(HttpMethod.POST, "/items", "original"), "body", false);
        MutableHttpRequest<?> mutable = MutableServerRequest.of(request);
        DirectByteBodyAccess direct = assertInstanceOf(DirectByteBodyAccess.class, mutable);

        assertSame(request.byteBody(), direct.byteBodyDirect());

        MutableHttpRequest<String> changed = mutable.body("replaced");

        assertSame(mutable, changed);
        assertNull(direct.byteBodyDirect());
        assertEquals("replaced", changed.getBody().orElseThrow());
    }

    @Test
    void aRequestWithoutACopyGetsAWrapperThatKeepsItsChanges() {
        ImmutableRequest request = new ImmutableRequest(new SimpleHttpRequest<>(HttpMethod.GET, "/items", "original"));

        MutableHttpRequest<?> mutable = MutableServerRequest.of(request);

        assertFalse(mutable instanceof MutableServerRequest<?>);
        assertEquals("/items", mutable.getPath());
        assertEquals("original", mutable.getBody().orElseThrow());

        mutable.uri(URI.create("/other/a%2Fb"));

        assertEquals("/other/a%2Fb", mutable.getPath());
        assertEquals("/items", request.getPath());
    }

    @Test
    void theWrapperHasNoBodyOnceItWasCleared() {
        MutableHttpRequest<?> mutable = MutableServerRequest.of(new ImmutableRequest(new SimpleHttpRequest<>(HttpMethod.POST, "/items", "original")));

        mutable.body(null);

        assertTrue(mutable.getBody().isEmpty());
        assertTrue(mutable.getBody(String.class).isEmpty());
        assertTrue(mutable.getBody(io.micronaut.core.convert.ConversionContext.STRING).isEmpty());
        assertNull(((DirectByteBodyAccess) mutable).byteBodyDirect());
    }

    @Test
    void theWrapperGivesTheBytesOfTheServerRequestItWraps() {
        ServerRequest server = new ServerRequest(new SimpleHttpRequest<>(HttpMethod.POST, "/items", null), "body");
        // a wrapper another filter continued with, around the server request
        ImmutableRequest wrapper = new ImmutableRequest(server);

        MutableHttpRequest<?> mutable = MutableServerRequest.mutable(wrapper);
        DirectByteBodyAccess direct = assertInstanceOf(DirectByteBodyAccess.class, mutable);

        assertSame(server.byteBody(), direct.byteBodyDirect());

        mutable.body("set");

        assertNull(direct.byteBodyDirect());
        assertEquals("set", mutable.getBody().orElseThrow());
    }

    @Test
    void theWrapperOfARequestWithoutBytesHasNone() {
        MutableHttpRequest<?> mutable = MutableServerRequest.mutable(new ImmutableRequest(new ImmutableRequest(new SimpleHttpRequest<>(HttpMethod.GET, "/", null))));

        assertNull(((DirectByteBodyAccess) mutable).byteBodyDirect());
    }

    @Test
    void theWrapperHidesTheBytesOfARequestWhoseWrapperReplacedTheBody() {
        ServerRequest server = new ServerRequest(new SimpleHttpRequest<>(HttpMethod.POST, "/items", "original"), "body");
        // a wrapper another filter continued with, which returns a sanitized body
        HttpRequestWrapper<Object> sanitized = new HttpRequestWrapper<>(server) {
            @Override
            public Optional<Object> getBody() {
                return Optional.of("replacement");
            }
        };

        MutableHttpRequest<?> mutable = MutableServerRequest.mutable(sanitized);

        assertNull(((DirectByteBodyAccess) mutable).byteBodyDirect());
        assertNull(((DirectByteBodyAccess) MutableServerRequest.of(sanitized)).byteBodyDirect());
        assertEquals("replacement", mutable.getBody().orElseThrow());
    }

    @Test
    void theWrapperKeepsTheBytesOfARequestWhoseWrapperKeptTheBody() {
        ServerRequest server = new ServerRequest(new SimpleHttpRequest<>(HttpMethod.POST, "/items", "original"), "body");

        MutableHttpRequest<?> mutable = MutableServerRequest.mutable(new ImmutableRequest(server));

        assertSame(server.byteBody(), ((DirectByteBodyAccess) mutable).byteBodyDirect());
    }

    @Test
    void theWrapperKeepsTheBytesOfARequestThatDecodesANewBodyOnEachCall() {
        // e.g. a server that decodes the body lazily: a plain wrapper around it did not replace the body
        ServerRequest server = new ServerRequest(new FreshBodyRequest(), "body");

        MutableHttpRequest<?> mutable = MutableServerRequest.mutable(new HttpRequestWrapper<>(server));

        assertSame(server.byteBody(), ((DirectByteBodyAccess) mutable).byteBodyDirect());
    }

    @Test
    void theWrapperAddsACookieToTheHeadersOfTheRequest() {
        SimpleHttpRequest<Object> request = new SimpleHttpRequest<>(HttpMethod.GET, "/items", null);
        MutableHttpRequest<?> mutable = MutableServerRequest.of(new ImmutableRequest(request));

        assertSame(mutable, mutable.cookie(Cookie.of("session", "abc")));

        assertEquals("session=abc", mutable.getHeaders().get("Cookie"));
        assertEquals("session=abc", request.getHeaders().get("Cookie"));
    }

    @Test
    void aFormRequestIsGivenACopyThatReadsItsForm() {
        FormRequest request = new FormRequest(new SimpleHttpRequest<>(HttpMethod.POST, "/form", null));

        MutableHttpRequest<?> mutable = MutableServerRequest.of(request);

        FormCapableHttpRequest<?> form = assertInstanceOf(FormCapableHttpRequest.class, mutable);
        assertTrue(form.hasFormBody());
        assertSame(request.fields, form.getRawFormFields());
        Runnable dispose = () -> { };
        form.addDisposalResource(dispose);
        assertEquals(List.of(dispose), request.disposed);
    }

    @Test
    void theCopyAFilterMethodWasGivenIsAServerRequestIfTheRequestIsOne() {
        ServerRequest server = new ServerRequest(new SimpleHttpRequest<>(HttpMethod.GET, "/items", null), "body");
        FormRequest form = new FormRequest(new SimpleHttpRequest<>(HttpMethod.POST, "/form", null));
        ImmutableRequest plain = new ImmutableRequest(new SimpleHttpRequest<>(HttpMethod.GET, "/plain", null));
        MutableHttpRequest<?> copy = new SimpleHttpRequest<>(HttpMethod.GET, "/copy", null);

        MutableHttpRequest<?> ofServer = MutableServerRequest.of(server, copy);
        MutableHttpRequest<?> ofForm = MutableServerRequest.of(form, copy);

        assertInstanceOf(MutableServerRequest.class, ofServer);
        assertNotSame(copy, ofServer);
        assertEquals("/copy", ofServer.getPath());
        assertInstanceOf(FormCapableHttpRequest.class, ofForm);
        assertSame(copy, MutableServerRequest.of(plain, copy));
    }

    /**
     * A request that cannot be mutated, like a wrapper a filter continued with.
     */
    private static class ImmutableRequest extends HttpRequestWrapper<Object> {
        @SuppressWarnings("unchecked")
        ImmutableRequest(HttpRequest<?> delegate) {
            super((HttpRequest<Object>) delegate);
        }
    }

    /**
     * A request that decodes a new body on each call.
     */
    private static final class FreshBodyRequest extends SimpleHttpRequest<Object> {
        FreshBodyRequest() {
            super(HttpMethod.POST, "/items", null);
        }

        @Override
        public Optional<Object> getBody() {
            return Optional.of(new Object());
        }
    }

    /**
     * A server request that is not mutable, whose copy is a mutable wrapper.
     */
    private static class ServerRequest extends HttpRequestWrapper<Object> implements ServerHttpRequest<Object> {
        private final ByteBody body;

        private final boolean copyable;

        ServerRequest(HttpRequest<?> delegate, String body) {
            this(delegate, body, true);
        }

        @SuppressWarnings("unchecked")
        ServerRequest(HttpRequest<?> delegate, String body, boolean copyable) {
            super((HttpRequest<Object>) delegate);
            this.body = BODIES.copyOf(body, java.nio.charset.StandardCharsets.UTF_8);
            this.copyable = copyable;
        }

        @Override
        public ByteBody byteBody() {
            return body;
        }

        @Override
        public ByteBodyFactory byteBodyFactory() {
            return BODIES;
        }

        @Override
        public MutableHttpRequest<Object> mutate() {
            if (!copyable) {
                return super.mutate();
            }
            return new SimpleHttpRequest<>(getMethod(), getUri().toString(), getBody().orElse(null));
        }

        @Override
        public HttpVersion getHttpVersion() {
            return HttpVersion.HTTP_2_0;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return REMOTE;
        }

        @Override
        public InetSocketAddress getServerAddress() {
            return LOCAL;
        }

        @Override
        public String getServerName() {
            return "server.example";
        }

        @Override
        public boolean isSecure() {
            return true;
        }
    }

    /**
     * A server request with a form body.
     */
    private static final class FormRequest extends ServerRequest implements FormCapableHttpRequest<Object> {
        private final Publisher<RawFormField> fields = Publishers.empty();
        private final List<Runnable> disposed = new ArrayList<>();

        FormRequest(HttpRequest<?> delegate) {
            super(delegate, "a=1");
        }

        @Override
        public Publisher<RawFormField> getRawFormFields() {
            return fields;
        }

        @Override
        public boolean hasFormBody() {
            return true;
        }

        @Override
        public void addDisposalResource(Runnable dispose) {
            disposed.add(dispose);
        }
    }
}
