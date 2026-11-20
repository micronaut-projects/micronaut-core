/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.web.router.resource;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import org.jspecify.annotations.Nullable;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Finds the pre-compressed sibling of a static resource, e.g. {@code app.js.br} of {@code app.js},
 * for the content codings a request accepts.
 *
 * <p>A sibling is a resource of the loader that has the original, at the path of the original
 * with the extension of the coding, so it is resolved exactly as the original is and can not leave
 * the base of the loader. Whether a resource of a jar or of a native image has a sibling is
 * remembered, as they do not change while the application runs; a file, of the file system or of
 * an exploded classpath, is checked for every request. On the classpath, the sibling must be in
 * the same entry, the same directory or jar, as the resource.</p>
 *
 * @since 5.3.0
 */
@Internal
public final class PreCompressedResources {

    /**
     * The most classpath lookups remembered. The siblings are only looked up for resources that
     * exist, so the bound is only reached by an application with very many resources.
     */
    private static final int MAX_REMEMBERED = 8192;

    private static final Coding[] CODINGS = Coding.values();
    private static final String FILE_PROTOCOL = "file";

    private final Map<ClasspathKey, Optional<URL>> classpathSiblings = new ConcurrentHashMap<>();

    /**
     * The codings of pre-compressed siblings a request accepts, from its {@code Accept-Encoding}
     * header values, the preferred first: by weight, then {@code br}, {@code zstd}, {@code gzip}.
     * A coding is left out if its weight is zero, if neither it nor {@code *} is listed, or if
     * the request explicitly gives {@code identity} a higher weight.
     *
     * @param acceptEncoding The values of the {@code Accept-Encoding} header
     * @return The acceptable codings, the preferred first; empty to serve the resource as it is
     */
    public static List<Coding> acceptable(Iterable<String> acceptEncoding) {
        float br = -1;
        float zstd = -1;
        float gzip = -1;
        float star = -1;
        float identity = -1;
        boolean any = false;
        for (String header : acceptEncoding) {
            int length = header.length();
            int start = 0;
            while (start < length) {
                int end = header.indexOf(',', start);
                if (end == -1) {
                    end = length;
                }
                int semicolon = header.indexOf(';', start);
                boolean parameters = semicolon != -1 && semicolon < end;
                String name = header.substring(start, parameters ? semicolon : end).trim().toLowerCase(Locale.ROOT);
                if (!name.isEmpty()) {
                    float q = parameters ? weight(header, semicolon + 1, end) : 1;
                    any = true;
                    switch (name) {
                        case "*" -> star = q;
                        case "identity" -> identity = Math.max(identity, q);
                        case "br" -> br = Math.max(br, q);
                        case "zstd" -> zstd = Math.max(zstd, q);
                        case "gzip", "x-gzip" -> gzip = Math.max(gzip, q);
                        default -> {
                            // a coding without pre-compressed siblings
                        }
                    }
                }
                start = end + 1;
            }
        }
        if (!any) {
            return List.of();
        }
        if (identity == -1) {
            // identity is acceptable unless excluded, and is the last choice unless weighted
            identity = star == -1 ? 0 : star;
        }
        List<Coding> acceptable = new ArrayList<>(CODINGS.length);
        // the weights of the acceptable codings, in their order
        float[] weights = new float[CODINGS.length];
        for (Coding coding : CODINGS) {
            float listed = switch (coding) {
                case BR -> br;
                case ZSTD -> zstd;
                case GZIP -> gzip;
            };
            float q = listed == -1 ? star : listed;
            if (q > 0 && q >= identity) {
                // insertion sort by weight, stable: the order of the constants breaks ties
                int i = acceptable.size();
                while (i > 0 && weights[i - 1] < q) {
                    weights[i] = weights[i - 1];
                    i--;
                }
                weights[i] = q;
                acceptable.add(i, coding);
            }
        }
        return acceptable;
    }

    /**
     * The weight of the parameters of an {@code Accept-Encoding} element.
     *
     * @param header The header value
     * @param start  The start of the parameters, inclusive
     * @param end    The end of the parameters, exclusive
     * @return The weight: 1 without a {@code q} parameter, 0 for a malformed one
     */
    private static float weight(String header, int start, int end) {
        while (start < end) {
            int next = header.indexOf(';', start);
            if (next == -1 || next > end) {
                next = end;
            }
            int equals = header.indexOf('=', start);
            if (equals != -1 && equals < next && "q".equalsIgnoreCase(header.substring(start, equals).trim())) {
                String value = header.substring(equals + 1, next).trim();
                if (value.isEmpty() || value.length() > 5) {
                    return 0;
                }
                for (int i = 0; i < value.length(); i++) {
                    char c = value.charAt(i);
                    if ((c < '0' || c > '9') && (c != '.' || i != 1)) {
                        return 0;
                    }
                }
                float q = Float.parseFloat(value);
                return q > 1 ? 0 : q;
            }
            start = next + 1;
        }
        return 1;
    }

    /**
     * Finds the pre-compressed sibling of a resource for the first of the given codings that has
     * one.
     *
     * @param original   The resource
     * @param codings    The codings, the preferred first, see {@link #acceptable(Iterable)}
     * @param acceptable Whether a resource of the loader is served; a sibling it refuses is a miss
     * @return The sibling and its coding, or {@code null} if no coding has one
     */
    public @Nullable Sibling find(StaticResourceResolver.ResolvedResource original, List<Coding> codings,
                                  Predicate<? super StaticResourceResolver.ResolvedResource> acceptable) {
        ResourceLoader loader = original.loader();
        String path = original.path();
        if (loader == null || path == null || path.isEmpty() || codings.isEmpty()) {
            return null;
        }
        boolean classpath = loader instanceof ClassPathResourceLoader;
        // a resource of a jar or of a native image does not change; a file of an exploded
        // classpath may, and is resolved again, with the checks of the loader, on every request
        boolean remember = classpath && !FILE_PROTOCOL.equals(original.url().getProtocol());
        for (Coding coding : codings) {
            String siblingPath = path + coding.extension;
            // a classpath loader searches several entries: the sibling of the entry of the resource
            String sameEntry = classpath ? original.url().toExternalForm() + coding.extension : null;
            Optional<URL> url;
            if (remember) {
                ClasspathKey key = new ClasspathKey(loader, siblingPath);
                url = classpathSiblings.get(key);
                if (url == null) {
                    url = lookup(loader, siblingPath, original.index(), acceptable, sameEntry);
                    if (classpathSiblings.size() < MAX_REMEMBERED) {
                        classpathSiblings.putIfAbsent(key, url);
                    }
                }
            } else {
                url = lookup(loader, siblingPath, original.index(), acceptable, sameEntry);
            }
            if (url.isPresent()) {
                return new Sibling(url.get(), coding);
            }
        }
        return null;
    }

    private static Optional<URL> lookup(ResourceLoader loader, String path, boolean index,
                                        Predicate<? super StaticResourceResolver.ResolvedResource> acceptable,
                                        @Nullable String sameEntry) {
        // the lookup of the original: the loader refuses a path that leaves its base
        return loader.getResource(path)
            .filter(url -> sameEntry == null || sameEntry.equals(url.toExternalForm()))
            .filter(url -> acceptable.test(new StaticResourceResolver.ResolvedResource(url, index, loader, path)));
    }

    /**
     * A content coding with pre-compressed siblings.
     */
    public enum Coding {
        /**
         * Brotli, {@code .br} files.
         */
        BR("br", ".br"),
        /**
         * Zstandard, {@code .zst} files.
         */
        ZSTD("zstd", ".zst"),
        /**
         * Gzip, {@code .gz} files.
         */
        GZIP("gzip", ".gz");

        private final String contentEncoding;
        private final String extension;

        Coding(String contentEncoding, String extension) {
            this.contentEncoding = contentEncoding;
            this.extension = extension;
        }

        /**
         * @return The value of the {@code Content-Encoding} header
         */
        public String contentEncoding() {
            return contentEncoding;
        }

        /**
         * @return The extension of the sibling, with the dot
         */
        public String extension() {
            return extension;
        }
    }

    /**
     * A pre-compressed sibling.
     *
     * @param url    The URL of the sibling
     * @param coding Its content coding
     */
    public record Sibling(URL url, Coding coding) {
    }

    private record ClasspathKey(ResourceLoader loader, String path) {
    }
}
