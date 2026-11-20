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

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * <p>A sibling is at the path of the original with the extension of the coding. In a directory of
 * the file system, it is resolved by the loader of the original, which refuses a path that leaves
 * its base. On the classpath, the sibling is the entry next to the resource, in the
 * same directory or jar, probed at its URL without searching the class loader: whether an entry
 * of a jar or of a native image exists is remembered, as they do not change while the application
 * runs; a file of an exploded classpath is checked for every request, and must be a regular file,
 * not a link.</p>
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

    private final Map<ClasspathKey, Lookup> classpathSiblings = new ConcurrentHashMap<>();

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
        File originalFile = classpath ? toFile(original.url()) : null;
        for (Coding coding : codings) {
            String siblingPath = path + coding.extension;
            Optional<URL> url;
            if (!classpath) {
                // the loader of a directory checks that the path stays under it
                url = loader.getResource(siblingPath);
            } else if (originalFile != null) {
                // an exploded classpath may change: the file next to the resource, checked on every request
                url = probeFile(originalFile, coding.extension);
            } else {
                // a jar or a native image does not change: the entry next to the resource, remembered
                ClasspathKey key = new ClasspathKey(loader, siblingPath);
                Lookup remembered = classpathSiblings.get(key);
                if (remembered != null) {
                    url = Optional.ofNullable(remembered.url());
                } else {
                    url = probeUrl(original.url().toExternalForm() + coding.extension);
                    if (classpathSiblings.size() < MAX_REMEMBERED) {
                        classpathSiblings.putIfAbsent(key, new Lookup(url.orElse(null)));
                    }
                }
            }
            Optional<URL> accepted = url.filter(candidate -> acceptable.test(
                new StaticResourceResolver.ResolvedResource(candidate, original.index(), loader, siblingPath)));
            if (accepted.isPresent()) {
                return new Sibling(accepted.get(), coding);
            }
        }
        return null;
    }

    /**
     * The sibling of a file of an exploded classpath: a regular file of the same directory, not a
     * link, so that it can not lead out of the directory of the resource. The classpath is not
     * searched: the sibling is of the entry of the resource.
     *
     * @param original  The file of the resource
     * @param extension The extension of the coding
     * @return The URL of the sibling, if there is one
     */
    private static Optional<URL> probeFile(File original, String extension) {
        File sibling = new File(original.getPath() + extension);
        Path siblingPath = sibling.toPath();
        if (!Files.isRegularFile(siblingPath, LinkOption.NOFOLLOW_LINKS) || !sibling.canRead()) {
            return Optional.empty();
        }
        try {
            return Optional.of(siblingPath.toUri().toURL());
        } catch (MalformedURLException e) {
            return Optional.empty();
        }
    }

    /**
     * The sibling of a resource of a jar or of a native image: the entry at the URL of the
     * resource with the extension of the coding, without searching the classpath.
     *
     * @param location The URL of the sibling
     * @return The URL, if the entry exists
     */
    private static Optional<URL> probeUrl(String location) {
        try {
            URL url = URI.create(location).toURL();
            URLConnection connection = url.openConnection();
            if (connection instanceof JarURLConnection jar) {
                return jar.getJarEntry() != null ? Optional.of(url) : Optional.empty();
            }
            // a missing entry fails to open
            connection.getInputStream().close();
            return Optional.of(url);
        } catch (IOException | IllegalArgumentException e) {
            // no such entry, or a location that is not a URI
            return Optional.empty();
        }
    }

    private static @Nullable File toFile(URL url) {
        if (!FILE_PROTOCOL.equals(url.getProtocol())) {
            return null;
        }
        try {
            return Paths.get(url.toURI()).toFile();
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
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

    /**
     * A remembered lookup of a sibling.
     *
     * @param url The URL of the sibling, or {@code null} if there is none
     */
    private record Lookup(@Nullable URL url) {
    }
}
