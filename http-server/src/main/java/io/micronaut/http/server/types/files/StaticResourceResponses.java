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
package io.micronaut.http.server.types.files;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.codec.CodecException;
import io.micronaut.web.router.resource.PreCompressedResources;
import io.micronaut.web.router.resource.StaticResourceCache;
import io.micronaut.web.router.resource.StaticResourceResolver;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Predicate;

/**
 * Builds the responses of static resources, with their pre-compressed siblings.
 *
 * @since 5.3.0
 */
@Internal
public final class StaticResourceResponses {

    private static final String FILE_PROTOCOL = "file";

    private StaticResourceResponses() {
    }

    /**
     * The body of a resource: a {@link SystemFile} for a readable file of the file system, else a
     * {@link StreamedFile}.
     *
     * @param url The URL of the resource
     * @return The body
     */
    public static FileCustomizableResponseType body(URL url) {
        return body(url, null, null);
    }

    /**
     * The body of a resource: from the cache when it has the resource, else a {@link SystemFile}
     * for a file of the file system, else a {@link StreamedFile}.
     *
     * @param url       The URL of the resource
     * @param mediaType The media type, or {@code null} for the one of the name of the resource
     * @param cache     The cache, or {@code null} for none
     * @return The body
     */
    private static FileCustomizableResponseType body(URL url, @Nullable MediaType mediaType, @Nullable StaticResourceCache cache) {
        if (cache != null) {
            StaticResourceCache.Entry entry = cache.get(url);
            if (entry != null) {
                return new CachedFile(entry.content(), mediaType != null ? mediaType : MediaType.forFilename(fileName(url)), entry.lastModified());
            }
        }
        if (mediaType != null) {
            return siblingBody(url, mediaType);
        }
        File file = toFile(url);
        if (file != null && file.isFile() && file.canRead()) {
            return new SystemFile(file);
        }
        return new StreamedFile(url);
    }

    /**
     * The response of a resource. With pre-compressed siblings, the response varies with the
     * {@code Accept-Encoding} header and has an entity tag; a sibling the request accepts is
     * served, with the content type of the resource, unless the request has a range, which is a
     * range of the resource itself.
     *
     * @param request       The request
     * @param resource      The resource
     * @param preCompressed The pre-compressed siblings, or {@code null} to serve the resource
     * @param cache         The in-memory cache, or {@code null} for none
     * @param acceptable    Whether a sibling is served; one it refuses is a miss
     * @return The response
     */
    public static MutableHttpResponse<?> respond(HttpRequest<?> request,
                                                 StaticResourceResolver.ResolvedResource resource,
                                                 @Nullable PreCompressedResources preCompressed,
                                                 @Nullable StaticResourceCache cache,
                                                 Predicate<? super StaticResourceResolver.ResolvedResource> acceptable) {
        boolean ranged = request.getMethod() == HttpMethod.GET && request.getHeaders().contains(HttpHeaders.RANGE);
        // a range is served from the file, which the file body writer supports
        StaticResourceCache bodyCache = ranged ? null : cache;
        if (preCompressed == null) {
            return HttpResponse.ok(body(resource.url(), null, bodyCache));
        }
        PreCompressedResources.Sibling sibling = null;
        if (!ranged) {
            List<PreCompressedResources.Coding> codings = PreCompressedResources.acceptable(request.getHeaders().getAll(HttpHeaders.ACCEPT_ENCODING));
            sibling = preCompressed.find(resource, codings, acceptable);
        }
        FileCustomizableResponseType body = sibling == null ? body(resource.url(), null, bodyCache)
            : body(sibling.url(), MediaType.forFilename(fileName(resource.url())), bodyCache);
        MutableHttpResponse<?> response = HttpResponse.ok(body);
        response.header(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
        if (sibling != null) {
            response.header(HttpHeaders.CONTENT_ENCODING, sibling.coding().contentEncoding());
        }
        response.header(HttpHeaders.ETAG, entityTag(body, sibling == null ? null : sibling.coding()));
        return response;
    }

    /**
     * A strong entity tag of a file and its coding: the codings of a resource have distinct tags.
     *
     * @param file   The file
     * @param coding The coding, or {@code null} for the resource itself
     * @return The entity tag, quoted
     */
    static String entityTag(FileCustomizableResponseType file, PreCompressedResources.@Nullable Coding coding) {
        StringBuilder tag = new StringBuilder("\"")
            .append(Long.toHexString(file.getLastModified()))
            .append('-')
            .append(Long.toHexString(file.getLength()));
        if (coding != null) {
            tag.append('-').append(coding.contentEncoding());
        }
        return tag.append('"').toString();
    }

    private static FileCustomizableResponseType siblingBody(URL url, MediaType mediaType) {
        File file = toFile(url);
        if (file != null) {
            return new SystemFile(file, mediaType);
        }
        try {
            URLConnection connection = url.openConnection();
            return new StreamedFile(connection.getInputStream(), mediaType, connection.getLastModified(), connection.getContentLengthLong());
        } catch (IOException e) {
            throw new CodecException("Could not open a connection to the URL: " + url.getPath(), e);
        }
    }

    private static String fileName(URL url) {
        String path = url.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * Whether a sibling of the configured static resources is served: a readable file of the file
     * system, or any other resource.
     *
     * @param resource The resource
     * @return Whether it is served
     */
    public static boolean isReadable(StaticResourceResolver.ResolvedResource resource) {
        if (!FILE_PROTOCOL.equals(resource.url().getProtocol())) {
            return true;
        }
        File file = toFile(resource.url());
        return file != null && file.isFile() && file.canRead();
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
}
