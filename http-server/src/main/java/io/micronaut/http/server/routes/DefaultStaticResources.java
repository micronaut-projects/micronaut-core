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
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        Optional<StaticResourceResolver.ResolvedResource> resolved;
        try {
            // a file another location may have exactly, when the file of a location is another
            // spelling of the path, or a directory
            resolved = StaticResourceResolver.resolveResource(loaders, path, indexFile, resource -> isServable(resource, path));
        } catch (InvalidPathException e) {
            // a name the file system does not allow, e.g. a? on Windows
            return HttpResponse.notFound();
        }
        if (resolved.isEmpty()) {
            return HttpResponse.notFound();
        }
        URL url = resolved.get().url();
        File file = FILE_PROTOCOL.equals(url.getProtocol()) ? toFile(url) : null;
        FileCustomizableResponseType body = file != null ? new SystemFile(file) : new StreamedFile(url);
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
        if (path.contains("//") || path.endsWith("/")) {
            // an empty segment: the router captures the path without the one trailing slash it
            // ignores, so a trailing slash of the path is another one
            return true;
        }
        String resource = path;
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
     * @param file  The file, whose path ends with the names of the request
     * @param path  The relative path of the resource, the file or the directory of its index file
     * @param index Whether the file is the index file of the path, not the path itself
     * @return Whether the names of the file system are the names of the request
     */
    static boolean isSpelledAsRequested(File file, String path, boolean index) {
        String resource = withoutTrailingSlash(path);
        String[] requested = resource.isEmpty() ? new String[0] : resource.split("/", -1);
        if (!endsWith(file, requested, index ? file.getName() : null)) {
            return false;
        }
        int names = requested.length + (index ? 1 : 0);
        File current = file;
        for (int i = 0; i < names; i++) {
            File directory = current.getParentFile();
            if (directory == null || !isListed(directory, current.getName())) {
                return false;
            }
            current = directory;
        }
        return true;
    }

    private static boolean endsWith(File file, String[] names, @Nullable String index) {
        File current = file;
        if (index != null) {
            current = current.getParentFile();
        }
        for (int i = names.length - 1; i >= 0; i--) {
            if (current == null || !names[i].equals(current.getName())) {
                return false;
            }
            current = current.getParentFile();
        }
        return true;
    }

    /**
     * Whether a directory lists a name exactly, as the same string: not another spelling the file
     * system resolves to the same file, e.g. another case or another Unicode normalization.
     *
     * @param directory The directory
     * @param name      The name of an existing file of the directory
     * @return Whether the name is the name the directory lists
     */
    private static boolean isListed(File directory, String name) {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory.toPath())) {
            for (Path entry : entries) {
                if (name.equals(entry.getFileName().toString())) {
                    return true;
                }
            }
            return false;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * @param path A path
     * @return The path without the one trailing slash the router ignores
     */
    private static String withoutTrailingSlash(String path) {
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    /**
     * Whether a resource of a location is served: a file of the file system that is a readable
     * file, not a directory, spelled as the path of the request, or any other resource.
     *
     * @param resource The resource of a location
     * @param path     The relative path of the request
     * @return Whether it is served
     */
    private static boolean isServable(StaticResourceResolver.ResolvedResource resource, String path) {
        URL url = resource.url();
        if (!FILE_PROTOCOL.equals(url.getProtocol())) {
            return true;
        }
        File file = toFile(url);
        return file != null && file.isFile() && file.canRead() && isSpelledAsRequested(file, path, resource.index());
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
