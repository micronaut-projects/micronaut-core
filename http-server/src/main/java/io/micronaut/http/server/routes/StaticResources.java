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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.file.DefaultFileSystemResourceLoader;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.server.types.files.FileCustomizableResponseType;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.server.types.files.SystemFile;
import io.micronaut.web.router.builder.PathVariables;
import io.micronaut.web.router.builder.ResourceHandler;
import io.micronaut.web.router.resource.StaticResourceResolver;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Static resources served by a handler route: the files under a directory, or the resources under
 * a package of the classpath, including those inside jars.
 *
 * <pre>{@code
 * routes.resources("/assets", StaticResources.classpath("public").cacheControl("public, max-age=31536000, immutable"));
 * routes.path("/docs", docs -> docs.resources("", StaticResources.fileSystem("/var/www/docs")));
 * routes.GET("/files/{+path}", StaticResources.of("file:/srv/files", "classpath:files").indexFile(null));
 * }</pre>
 *
 * <p>The handler reads the path of the resource, relative to the base, from the path variable
 * {@link #pathVariable()}, {@code path} by default, see
 * {@link io.micronaut.web.router.builder.HttpRouteBuilder#resources(String, ResourceHandler)}. It
 * answers {@code 404} when no location has the resource, when the path is a directory without an
 * index file, and when the path tries to leave the base: a {@code .} or {@code ..} segment, a
 * backslash, an absolute path or a percent-encoded dot, slash or backslash.</p>
 *
 * <p>A file of the file system is a {@link SystemFile}, and any other resource, e.g. in a jar, a
 * {@link StreamedFile}. The file body writers of the server answer them like the files of the
 * static resources of {@code micronaut.router.static-resources}: the content type from the file
 * name, the {@code Last-Modified} header, {@code 304} to a matching {@code If-Modified-Since},
 * and, for the files of the file system, {@code 206} to a {@code Range}. A {@code HEAD} request
 * is answered with the content type, and for a file of the file system the length and the
 * {@code Last-Modified} header, without opening the resource.</p>
 *
 * <p>The instances are immutable: each option returns a copy with the option set.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class StaticResources implements ResourceHandler {

    /**
     * The default name of the path variable of the path of the resource.
     */
    public static final String DEFAULT_PATH_VARIABLE = "path";

    /**
     * The default index file of a directory.
     */
    public static final String DEFAULT_INDEX_FILE = "index.html";

    private static final String FILE_PROTOCOL = "file";
    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String[] ENCODED_SEPARATORS = {"%2e", "%2f", "%5c", "%00"};

    private final List<ResourceLoader> loaders;
    private final String pathVariable;
    private final @Nullable String indexFile;
    private final @Nullable String cacheControl;

    private StaticResources(List<ResourceLoader> loaders, String pathVariable, @Nullable String indexFile, @Nullable String cacheControl) {
        this.loaders = loaders;
        this.pathVariable = pathVariable;
        this.indexFile = indexFile;
        this.cacheControl = cacheControl;
    }

    /**
     * The resources under a package of the classpath, e.g. {@code public} for
     * {@code src/main/resources/public}.
     *
     * @param base The base path of the resources on the classpath
     * @return The static resources
     */
    public static StaticResources classpath(String base) {
        Objects.requireNonNull(base, "base");
        return of(CLASSPATH_PREFIX + base);
    }

    /**
     * The files under a directory of the file system. Symbolic links that lead outside the
     * directory are not followed.
     *
     * @param base The directory
     * @return The static resources
     */
    public static StaticResources fileSystem(Path base) {
        Objects.requireNonNull(base, "base");
        return of(new DefaultFileSystemResourceLoader(base));
    }

    /**
     * The files under a directory of the file system.
     *
     * @param base The path of the directory
     * @return The static resources
     * @see #fileSystem(Path)
     */
    public static StaticResources fileSystem(String base) {
        Objects.requireNonNull(base, "base");
        return fileSystem(Paths.get(base));
    }

    /**
     * The resources of resource loaders, whose base is the base of the resources: the first
     * loader that has a resource provides it.
     *
     * @param loaders The resource loaders
     * @return The static resources
     */
    public static StaticResources of(ResourceLoader... loaders) {
        Objects.requireNonNull(loaders, "loaders");
        if (loaders.length == 0) {
            throw new IllegalArgumentException("Static resources need at least one location");
        }
        return new StaticResources(List.of(loaders), DEFAULT_PATH_VARIABLE, DEFAULT_INDEX_FILE, null);
    }

    /**
     * The resources of locations with a prefix, like the paths of
     * {@code micronaut.router.static-resources}: {@code classpath:public} or
     * {@code file:/var/www}. The first location that has a resource provides it.
     *
     * @param locations The locations
     * @return The static resources
     * @throws IllegalArgumentException if a location has no supported prefix
     */
    public static StaticResources of(String... locations) {
        Objects.requireNonNull(locations, "locations");
        ResourceResolver resolver = new ResourceResolver();
        ResourceLoader[] loaders = new ResourceLoader[locations.length];
        for (int i = 0; i < locations.length; i++) {
            String location = Objects.requireNonNull(locations[i], "location");
            loaders[i] = resolver.getLoaderForBasePath(location)
                .orElseThrow(() -> new IllegalArgumentException("Static resources support the locations with the prefix classpath: or file:, not " + location));
        }
        return of(loaders);
    }

    /**
     * The name of the path variable of the path of the resource, relative to the base, e.g.
     * {@code file} for {@code routes.GET("/files/{+file}", resources.pathVariable("file"))}.
     *
     * @param name The name of the path variable, {@value #DEFAULT_PATH_VARIABLE} by default
     * @return A copy with the path variable
     */
    public StaticResources pathVariable(String name) {
        Objects.requireNonNull(name, "name");
        return new StaticResources(loaders, name, indexFile, cacheControl);
    }

    /**
     * The index file of a directory: the resource of an empty path, e.g. the URI prefix of
     * {@link io.micronaut.web.router.builder.HttpRouteBuilder#resources(String, ResourceHandler)},
     * and of a path without an extension that is not a resource, e.g. {@code docs} for
     * {@code docs/index.html}.
     *
     * @param name The name of the index file, {@value #DEFAULT_INDEX_FILE} by default, or {@code null} for none
     * @return A copy with the index file
     */
    public StaticResources indexFile(@Nullable String name) {
        return new StaticResources(loaders, pathVariable, name, cacheControl);
    }

    /**
     * The {@code Cache-Control} header of the resources, e.g.
     * {@code public, max-age=31536000, immutable}. By default, the header of the files of the
     * server, see {@code micronaut.server.responses.file}.
     *
     * @param value The value of the header, or {@code null} for the default
     * @return A copy with the header
     */
    public StaticResources cacheControl(@Nullable String value) {
        return new StaticResources(loaders, pathVariable, indexFile, value);
    }

    @Override
    public String pathVariable() {
        return pathVariable;
    }

    /**
     * @return The name of the index file of a directory, or {@code null} for none
     */
    public @Nullable String indexFile() {
        return indexFile;
    }

    /**
     * @return The {@code Cache-Control} header of the resources, or {@code null} for the default
     */
    public @Nullable String cacheControl() {
        return cacheControl;
    }

    @Override
    public HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables) {
        String path = pathVariables.findString(pathVariable).orElse("");
        if (!isRelative(path)) {
            return HttpResponse.notFound();
        }
        Optional<URL> resolved = StaticResourceResolver.resolve(loaders, path, indexFile);
        if (resolved.isEmpty()) {
            return HttpResponse.notFound();
        }
        URL url = resolved.get();
        File file = null;
        if (FILE_PROTOCOL.equals(url.getProtocol())) {
            file = toFile(url);
            if (file == null || !file.isFile() || !file.canRead()) {
                // a resource loader may resolve a directory
                return HttpResponse.notFound();
            }
        }
        MutableHttpResponse<?> response;
        if (request.getMethod() == HttpMethod.HEAD) {
            // the server drops the body of a HEAD response without writing it: the headers the
            // body writer would add, without opening the resource
            response = HttpResponse.ok();
            if (file != null) {
                response.contentType(MediaType.forFilename(file.getName()));
                response.contentLength(file.length());
                response.getHeaders().lastModified(file.lastModified());
            } else {
                String urlPath = url.getPath();
                response.contentType(MediaType.forFilename(urlPath.substring(urlPath.lastIndexOf('/') + 1)));
            }
        } else {
            FileCustomizableResponseType body = file != null ? new SystemFile(file) : new StreamedFile(url);
            response = HttpResponse.ok(body);
        }
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
