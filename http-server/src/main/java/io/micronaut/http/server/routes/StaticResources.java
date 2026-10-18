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
import io.micronaut.web.router.builder.ResourceHandler;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

/**
 * Static resources served by a handler route: the files under a directory, or the resources under
 * a package of the classpath, including those inside jars.
 *
 * <pre>{@code
 * routes.GET("/assets").resources(StaticResources.classpath("public").cacheControl("public, max-age=31536000, immutable"));
 * routes.path("/docs", docs -> docs.GET("").resources(StaticResources.fileSystem("/var/www/docs")));
 * routes.GET("/files/{+path}", StaticResources.of("file:/srv/files", "classpath:files").indexFile(null));
 * }</pre>
 *
 * <p>The handler reads the path of the resource, relative to the base, from the path variable
 * {@link #pathVariable()}, {@code path} by default, see
 * {@link io.micronaut.web.router.builder.HttpRouteSpec#resources(ResourceHandler)}. It
 * answers {@code 404} when no location has the resource, when the path is a directory without an
 * index file, when the path tries to leave the base: a {@code .} or {@code ..} segment, a
 * backslash, an absolute path or a percent-encoded dot, slash or backslash, and when a segment
 * of the path is hidden, i.e. starts with a dot, e.g. {@code .env}, unless
 * {@link #hiddenFiles(boolean)} serves them.</p>
 *
 * <p>A file of the file system is a {@link io.micronaut.http.server.types.files.SystemFile}, and
 * any other resource, e.g. in a jar, a {@link io.micronaut.http.server.types.files.StreamedFile}.
 * The file body writers of the server answer them like the files of the static resources of
 * {@code micronaut.router.static-resources}, to {@code GET} and {@code HEAD} alike: the content
 * type from the file name, the {@code Last-Modified} and cache headers, {@code 304} to a matching
 * {@code If-Modified-Since}, and, for the files of the file system, {@code 206} to a
 * {@code Range}.</p>
 *
 * <p>Reading the resources blocks, so the routes of resources run on the blocking executor,
 * unless the route or its group declares an executor, see {@link ResourceHandler#blocking()}.</p>
 *
 * <p>The instances are immutable: each option returns a copy with the option set.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface StaticResources extends ResourceHandler permits DefaultStaticResources {

    /**
     * The default name of the path variable of the path of the resource.
     */
    String DEFAULT_PATH_VARIABLE = "path";

    /**
     * The default index file of a directory.
     */
    String DEFAULT_INDEX_FILE = "index.html";

    /**
     * The resources under a package of the classpath, e.g. {@code public} for
     * {@code src/main/resources/public}.
     *
     * @param base The base path of the resources on the classpath
     * @return The static resources
     */
    static StaticResources classpath(String base) {
        Objects.requireNonNull(base, "base");
        return of(DefaultStaticResources.CLASSPATH_PREFIX + base);
    }

    /**
     * The files under a directory of the file system. Symbolic links that lead outside the
     * directory are not followed.
     *
     * @param base The directory
     * @return The static resources
     */
    static StaticResources fileSystem(Path base) {
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
    static StaticResources fileSystem(String base) {
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
    static StaticResources of(ResourceLoader... loaders) {
        Objects.requireNonNull(loaders, "loaders");
        if (loaders.length == 0) {
            throw new IllegalArgumentException("Static resources need at least one location");
        }
        return new DefaultStaticResources(List.of(loaders), DEFAULT_PATH_VARIABLE, DEFAULT_INDEX_FILE, null, false);
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
    static StaticResources of(String... locations) {
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
    StaticResources pathVariable(String name);

    /**
     * The index file of a directory: the resource of an empty path, e.g. the URI prefix of
     * {@link io.micronaut.web.router.builder.HttpRouteSpec#resources(ResourceHandler)},
     * and of a path without an extension that is not a resource, e.g. {@code docs} for
     * {@code docs/index.html}.
     *
     * @param name The name of the index file, {@value #DEFAULT_INDEX_FILE} by default, or {@code null} for none
     * @return A copy with the index file
     */
    StaticResources indexFile(@Nullable String name);

    /**
     * The {@code Cache-Control} header of the resources, e.g.
     * {@code public, max-age=31536000, immutable}. By default, the header of the files of the
     * server, see {@code micronaut.server.responses.file}.
     *
     * @param value The value of the header, or {@code null} for the default
     * @return A copy with the header
     */
    StaticResources cacheControl(@Nullable String value);

    /**
     * Whether the hidden files are served: the paths with a segment that starts with a dot, e.g.
     * {@code .env}, {@code .git/config} or {@code .well-known/security.txt}. They are not by
     * default: such a path is answered with {@code 404}.
     *
     * @param serve Whether to serve them
     * @return A copy with the option
     */
    StaticResources hiddenFiles(boolean serve);

    /**
     * @return The name of the index file of a directory, or {@code null} for none
     */
    @Nullable String indexFile();

    /**
     * @return The {@code Cache-Control} header of the resources, or {@code null} for the default
     */
    @Nullable String cacheControl();

    /**
     * @return Whether the hidden files are served
     */
    boolean hiddenFiles();
}
