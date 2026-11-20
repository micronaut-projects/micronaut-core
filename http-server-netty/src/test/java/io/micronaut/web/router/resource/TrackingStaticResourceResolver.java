package io.micronaut.web.router.resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Resolves one resource to a non-file URL and counts the streams opened and closed for it.
 */
public class TrackingStaticResourceResolver extends StaticResourceResolver {

    public final AtomicInteger opened = new AtomicInteger();
    public final AtomicInteger closed = new AtomicInteger();
    private final String path;

    public TrackingStaticResourceResolver(String path) {
        super(Collections.emptyList());
        this.path = path;
    }

    @Override
    public Optional<URL> resolve(String resourcePath) {
        if (!path.equals(resourcePath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new URL(null, "tracked:" + path, new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL u) {
                    return new URLConnection(u) {
                        @Override
                        public void connect() {
                            // nothing to connect, the stream is in memory
                        }

                        @Override
                        public InputStream getInputStream() {
                            opened.incrementAndGet();
                            return new ByteArrayInputStream("streamed-report".getBytes(StandardCharsets.UTF_8)) {
                                @Override
                                public void close() {
                                    closed.incrementAndGet();
                                }
                            };
                        }
                    };
                }
            }));
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }
}
