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
package io.micronaut.http.server.routes;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.PathVariables;
import io.micronaut.http.server.types.files.FileCustomizableResponseType;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.server.types.files.SystemFile;
import io.micronaut.web.router.resource.StaticResourceResolver;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The static resources of resource loaders.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultStaticResources implements StaticResources {

    static final String CLASSPATH_PREFIX = "classpath:";

    private static final String FILE_PROTOCOL = "file";
    private static final String[] ENCODED_SEPARATORS = {"%2e", "%2f", "%5c", "%00"};

    private final List<ResourceLoader> loaders;
    private final String pathVariable;
    private final @Nullable String indexFile;
    private final @Nullable String cacheControl;
    private final boolean hiddenFiles;

    DefaultStaticResources(List<ResourceLoader> loaders, String pathVariable, @Nullable String indexFile,
                           @Nullable String cacheControl, boolean hiddenFiles) {
        this.loaders = loaders;
        this.pathVariable = pathVariable;
        this.indexFile = indexFile;
        this.cacheControl = cacheControl;
        this.hiddenFiles = hiddenFiles;
    }

    @Override
    public StaticResources pathVariable(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty() || !name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_')) {
            throw new IllegalArgumentException("The path variable of static resources must be a plain name: " + name);
        }
        return new DefaultStaticResources(loaders, name, indexFile, cacheControl, hiddenFiles);
    }

    @Override
    public StaticResources indexFile(@Nullable String name) {
        if (name != null && (name.isEmpty() || ".".equals(name) || "..".equals(name)
            || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0)) {
            throw new IllegalArgumentException("The index file of static resources must be the name of a file: " + name);
        }
        return new DefaultStaticResources(loaders, pathVariable, name, cacheControl, hiddenFiles);
    }

    @Override
    public StaticResources cacheControl(@Nullable String value) {
        return new DefaultStaticResources(loaders, pathVariable, indexFile, value, hiddenFiles);
    }

    @Override
    public StaticResources hiddenFiles(boolean serve) {
        return new DefaultStaticResources(loaders, pathVariable, indexFile, cacheControl, serve);
    }

    @Override
    public String pathVariable() {
        return pathVariable;
    }

    @Override
    public @Nullable String indexFile() {
        return indexFile;
    }

    @Override
    public @Nullable String cacheControl() {
        return cacheControl;
    }

    @Override
    public boolean hiddenFiles() {
        return hiddenFiles;
    }

    @Override
    public boolean blocking() {
        // the loaders of the file system and of the classpath read files and jars
        return true;
    }

    @Override
    public HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables) {
        String path = pathVariables.findString(pathVariable).orElse("");
        if (!isRelative(path) || (!hiddenFiles && isHidden(path)) || isAlias(path, request.getUri().getRawPath())
            || spansSegments(pathVariables)) {
            return HttpResponse.notFound();
        }
        Optional<URL> resolved;
        try {
            resolved = StaticResourceResolver.resolve(loaders, path, indexFile);
        } catch (InvalidPathException e) {
            // a name the file system does not allow, e.g. a? on Windows
            return HttpResponse.notFound();
        }
        if (resolved.isEmpty()) {
            return HttpResponse.notFound();
        }
        URL url = resolved.get();
        FileCustomizableResponseType body;
        if (FILE_PROTOCOL.equals(url.getProtocol())) {
            File file = toFile(url);
            if (file == null || !file.isFile() || !file.canRead() || !isSpelledAsRequested(file, path)) {
                // a resource loader may resolve a directory
                return HttpResponse.notFound();
            }
            body = new SystemFile(file);
        } else {
            body = new StreamedFile(url);
        }
        // a HEAD response too: the server writes the body to set the headers of a GET response,
        // e.g. 304 to a matching If-Modified-Since, and discards the content
        MutableHttpResponse<?> response = HttpResponse.ok(body);
        if (cacheControl != null) {
            // the file body writers keep the header of the response
            response.header(HttpHeaders.CACHE_CONTROL, cacheControl);
        }
        return response;
    }

    /**
     * Whether a path is relative to the base and stays under it.
     *
     * @param path The decoded path of the resource
     * @return Whether it is a relative path without segments that leave the base
     */
    static boolean isRelative(String path) {
        if (path.isEmpty()) {
            return true;
        }
        if (path.charAt(0) == '/') {
            // absolute
            return false;
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '\\' || c == ':' || c == '\0') {
                // a Windows separator or drive, or a truncated name
                return false;
            }
        }
        if (path.indexOf('%') >= 0) {
            // the path is decoded once: a remaining encoded separator was encoded twice
            String lowerCase = path.toLowerCase(Locale.ROOT);
            for (String encoded : ENCODED_SEPARATORS) {
                if (lowerCase.contains(encoded)) {
                    return false;
                }
            }
        }
        int start = 0;
        while (start <= path.length()) {
            int end = path.indexOf('/', start);
            if (end == -1) {
                end = path.length();
            }
            int length = end - start;
            if ((length == 1 || length == 2) && path.charAt(start) == '.' && (length == 1 || path.charAt(start + 1) == '.')) {
                return false;
            }
            start = end + 1;
        }
        return true;
    }

    /**
     * Whether a path is another spelling of a path that the router may route elsewhere, e.g. to a
     * more specific route: an empty segment, as in {@code css//site.css}, or an encoded slash, as
     * in {@code css%2fsite.css}, which the router matches as one segment. Only the end of the raw
     * path of the request, the path of the resource, is checked, not the prefix, and it is not
     * decoded, so the check does not depend on the charset of the router.
     *
     * @param path    The decoded path of the resource
     * @param rawPath The raw path of the request
     * @return Whether it is an alias
     */
    static boolean isAlias(String path, @Nullable String rawPath) {
        String resource = withoutTrailingSlash(path);
        if (resource.contains("//")) {
            return true;
        }
        if (resource.isEmpty() || rawPath == null || rawPath.indexOf('%') == -1) {
            return false;
        }
        // the router ignores a trailing slash
        String raw = withoutTrailingSlash(rawPath);
        // as many raw segments as decoded ones: with an encoded slash in the path of the
        // resource, they reach the prefix, and have the encoded slash
        int start = raw.length();
        for (int i = 0; i < segments(resource); i++) {
            start = raw.lastIndexOf('/', start - 1);
            if (start < 0) {
                return true;
            }
        }
        String rawResource = raw.substring(start + 1).toLowerCase(Locale.ROOT);
        return rawResource.contains("%2f");
    }

    private static String withoutTrailingSlash(String path) {
        int end = path.length();
        while (end > 0 && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(0, end);
    }

    private static int segments(String path) {
        int segments = 1;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') {
                segments++;
            }
        }
        return segments;
    }

    /**
     * Whether a variable of the route other than the path of the resource matched several
     * segments or none, e.g. a variable of the prefix whose regular expression matches a slash or
     * an empty value: the path of the resource is then not the end of the path of the request.
     *
     * @param pathVariables The path variables of the route
     * @return Whether a variable spans segments
     */
    private boolean spansSegments(PathVariables pathVariables) {
        for (String name : pathVariables.names()) {
            if (!name.equals(pathVariable)
                && pathVariables.findString(name).filter(value -> value.isEmpty() || value.indexOf('/') >= 0).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a file of the file system is spelled as the path of the request, e.g. not
     * {@code HELLO.TXT} for {@code hello.txt} on a file system that ignores the case, e.g. on
     * macOS or Windows, which the router may route elsewhere, e.g. to a more specific route. The
     * names of the path, and of the index file, are compared with the names their directories
     * list, exactly: only the names under the base, not the directories above it, and without
     * following the links, which the loaders check.
     *
     * @param file The file, whose path ends with the names of the request
     * @param path The relative path of the resource, the file or the directory of its index file
     * @return Whether the names of the file system are the names of the request
     */
    static boolean isSpelledAsRequested(File file, String path) {
        String resource = withoutTrailingSlash(path);
        int names = resource.isEmpty() ? 0 : segments(resource);
        if (names == 0 || !file.getName().equals(resource.substring(resource.lastIndexOf('/') + 1))) {
            // the index file of the directory
            names++;
        }
        File current = file;
        for (int i = 0; i < names && current != null; i++) {
            File directory = current.getParentFile();
            String[] listed = directory == null ? null : directory.list();
            if (listed == null || !Arrays.asList(listed).contains(current.getName())) {
                return false;
            }
            current = directory;
        }
        return current != null;
    }

    /**
     * Whether a path has a hidden segment, one that starts with a dot.
     *
     * @param path The relative path of the resource
     * @return Whether a segment is hidden
     */
    static boolean isHidden(String path) {
        int start = 0;
        while (start < path.length()) {
            if (path.charAt(start) == '.') {
                return true;
            }
            int end = path.indexOf('/', start);
            if (end == -1) {
                return false;
            }
            start = end + 1;
        }
        return false;
    }

    private static @Nullable File toFile(URL url) {
        try {
            return Paths.get(url.toURI()).toFile();
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "StaticResources" + loaders;
    }
}
