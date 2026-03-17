/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.io.value;

import io.micronaut.core.io.ResourceLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * {@link ResourceLoader} implementation with a fixed value.
 *
 * @since 4.10.0
 * @author Jonas Konrad
 */
abstract sealed class ValueResourceLoader implements ResourceLoader permits Base64ResourceLoader, StringResourceLoader {
    private final String prefix;

    ValueResourceLoader(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public final ResourceLoader forBase(String basePath) {
        throw new UnsupportedOperationException(
            "This resource loader (" + prefix + ") does not support paths");
    }

    @Override
    public final boolean supportsPrefix(String path) {
        return path.startsWith(prefix);
    }

    @Override
    public final Stream<URL> getResources(String name) {
        return getResource(name).stream();
    }

    @Override
    public final Optional<URL> getResource(String path) {
        return extractWithPrefix(path).map(bytes -> {
            String scheme = prefix;
            if (scheme.endsWith(":")) {
                scheme = scheme.substring(0, scheme.length() - 1);
            }
            try {
                return new URL(scheme, null, -1, path.substring(scheme.length()), new StreamHandlerImpl(bytes));
            } catch (MalformedURLException e) {
                throw new AssertionError("Shouldn't happen since host is null", e);
            }
        });
    }

    @Override
    public final Optional<InputStream> getResourceAsStream(String path) {
        return extractWithPrefix(path).map(ByteArrayInputStream::new);
    }

    private Optional<byte[]> extractWithPrefix(String path) {
        if (!supportsPrefix(path)) {
            throw new IllegalArgumentException("Unexpected prefix: " + path);
        }
        return extract(path.substring(prefix.length()));
    }

    protected abstract Optional<byte[]> extract(String value);

    private static final class StreamHandlerImpl extends URLStreamHandler {
        private final byte[] bytes;

        StreamHandlerImpl(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            return new FixedURLConnection(u, bytes);
        }
    }

    private static final class FixedURLConnection extends URLConnection {
        private final InputStream stream;
        private final int length;

        FixedURLConnection(URL url, byte[] bytes) {
            super(url);
            this.length = bytes.length;
            this.stream = new ByteArrayInputStream(bytes);
        }

        @Override
        public void connect() {
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return stream;
        }

        @Override
        public long getContentLengthLong() {
            return length;
        }
    }
}
