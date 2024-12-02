/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.netty.body.AvailableNettyByteBody;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.types.files.FileCustomizableResponseType;
import io.netty.handler.codec.http.HttpHeaderNames;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

/**
 * Abstract implementation for types that write files.
 */
@Experimental
@Internal
abstract sealed class AbstractFileBodyWriter permits InputStreamBodyWriter, StreamFileBodyWriter, SystemFileBodyWriter {
    private static final String[] ENTITY_HEADERS = {HttpHeaders.ALLOW, HttpHeaders.CONTENT_ENCODING, HttpHeaders.CONTENT_LANGUAGE, HttpHeaders.CONTENT_LENGTH, HttpHeaders.CONTENT_LOCATION, HttpHeaders.CONTENT_MD5, HttpHeaders.CONTENT_RANGE, HttpHeaders.CONTENT_TYPE, HttpHeaders.EXPIRES, HttpHeaders.LAST_MODIFIED};
    protected final NettyHttpServerConfiguration.FileTypeHandlerConfiguration configuration;

    AbstractFileBodyWriter(NettyHttpServerConfiguration.FileTypeHandlerConfiguration configuration) {
        this.configuration = configuration;
    }

    private static void copyNonEntityHeaders(MutableHttpResponse<?> from, MutableHttpResponse to) {
        from.getHeaders().forEachValue((header, value) -> {
            if (Arrays.binarySearch(ENTITY_HEADERS, header) < 0) {
                to.getHeaders().add(header, value);
            }
        });
    }

    protected boolean handleIfModifiedAndHeaders(HttpRequest<?> request, MutableHttpResponse<?> response, FileCustomizableResponseType systemFile, MutableHttpResponse<?> nettyResponse) {
        long lastModified = systemFile.getLastModified();

        // Cache Validation
        ZonedDateTime ifModifiedSince = request.getHeaders().getDate(HttpHeaders.IF_MODIFIED_SINCE);
        if (ifModifiedSince != null) {

            // Only compare up to the second because the datetime format we send to the client
            // does not have milliseconds
            long ifModifiedSinceDateSeconds = ifModifiedSince.toEpochSecond();
            long fileLastModifiedSeconds = lastModified / 1000;
            if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
                return true;
            }
        }

        if (!response.getHeaders().contains(HttpHeaderNames.CONTENT_TYPE)) {
            response.header(HttpHeaderNames.CONTENT_TYPE, systemFile.getMediaType().toString());
        }
        setDateAndCacheHeaders(response, lastModified);
        systemFile.process(nettyResponse);
        return false;
    }

    /**
     * @param response     The Http response
     * @param lastModified The last modified
     */
    protected void setDateAndCacheHeaders(MutableHttpResponse response, long lastModified) {
        // Date header
        MutableHttpHeaders headers = response.getHeaders();
        LocalDateTime now = LocalDateTime.now();
        if (!headers.contains(HttpHeaderNames.DATE)) {
            headers.date(now);
        }

        // Add cache headers
        LocalDateTime cacheSeconds = now.plus(configuration.getCacheSeconds(), ChronoUnit.SECONDS);
        if (response.header(HttpHeaders.EXPIRES) == null) {
            headers.expires(cacheSeconds);
        }

        if (response.header(HttpHeaders.CACHE_CONTROL) == null) {
            NettyHttpServerConfiguration.FileTypeHandlerConfiguration.CacheControlConfiguration cacheConfig = configuration.getCacheControl();
            StringBuilder header = new StringBuilder(cacheConfig.getPublic() ? "public" : "private")
                .append(", max-age=")
                .append(configuration.getCacheSeconds());
            response.header(HttpHeaders.CACHE_CONTROL, header.toString());
        }

        if (response.header(HttpHeaders.LAST_MODIFIED) == null) {
            headers.lastModified(lastModified);
        }
    }

    /**
     * @param response The Http response
     */
    protected void setDateHeader(MutableHttpResponse response) {
        MutableHttpHeaders headers = response.getHeaders();
        LocalDateTime now = LocalDateTime.now();
        headers.date(now);
    }

    protected ByteBodyHttpResponse<?> notModified(MutableHttpResponse<?> originalResponse) {
        MutableHttpResponse<Void> response = HttpResponse.notModified();
        AbstractFileBodyWriter.copyNonEntityHeaders(originalResponse, response);
        setDateHeader(response);
        return ByteBodyHttpResponseWrapper.wrap(response, AvailableNettyByteBody.empty());
    }
}
