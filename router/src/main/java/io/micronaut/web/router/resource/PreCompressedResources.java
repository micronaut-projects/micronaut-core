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
import java.util.Arrays;
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
 * the base of the loader. Whether a classpath location has a sibling is remembered, as the
 * classpath does not change while the application runs; a location of the file system is checked
 * for every request.</p>
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
        float[] weights = new float[CODINGS.length];
        Arrays.fill(weights, -1);
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
                        case "br" -> weights[Coding.BR.ordinal()] = Math.max(weights[Coding.BR.ordinal()], q);
                        case "zstd" -> weights[Coding.ZSTD.ordinal()] = Math.max(weights[Coding.ZSTD.ordinal()], q);
                        case "gzip", "x-gzip" -> weights[Coding.GZIP.ordinal()] = Math.max(weights[Coding.GZIP.ordinal()], q);
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
        for (Coding coding : CODINGS) {
            float q = weights[coding.ordinal()];
            if (q == -1) {
                q = star;
                weights[coding.ordinal()] = q;
            }
            if (q > 0 && q >= identity) {
                // insertion sort by weight, stable: the order of the constants breaks ties
                int i = acceptable.size();
                while (i > 0 && weights[acceptable.get(i - 1).ordinal()] < q) {
                    i--;
                }
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
        boolean remember = loader instanceof ClassPathResourceLoader;
        for (Coding coding : codings) {
            String siblingPath = path + coding.extension;
            Optional<URL> url;
            if (remember) {
                ClasspathKey key = new ClasspathKey(loader, siblingPath);
                url = classpathSiblings.get(key);
                if (url == null) {
                    url = lookup(loader, siblingPath, original.index(), acceptable);
                    if (classpathSiblings.size() < MAX_REMEMBERED) {
                        classpathSiblings.putIfAbsent(key, url);
                    }
                }
            } else {
                url = lookup(loader, siblingPath, original.index(), acceptable);
            }
            if (url.isPresent()) {
                return new Sibling(url.get(), coding);
            }
        }
        return null;
    }

    private static Optional<URL> lookup(ResourceLoader loader, String path, boolean index,
                                        Predicate<? super StaticResourceResolver.ResolvedResource> acceptable) {
        // the lookup of the original: the loader refuses a path that leaves its base
        return loader.getResource(path)
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
