/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.types.files;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.server.netty.types.NettyCustomizableResponseTypeHandler;
import io.micronaut.http.server.netty.types.NettyFileCustomizableResponseType;
import io.micronaut.http.server.types.CustomizableResponseTypeException;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.server.types.files.SystemFile;
import io.micronaut.http.server.types.files.SystemFileCustomizableResponseType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpResponse;

import javax.inject.Singleton;
import java.io.File;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

/**
 * Responsible for writing files out to the response in Netty.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
public class FileTypeHandler implements NettyCustomizableResponseTypeHandler<Object> {

    private static final Class<?>[] SUPPORTED_TYPES = new Class[]{File.class, SystemFileCustomizableResponseType.class, StreamedFile.class, NettyFileCustomizableResponseType.class, SystemFile.class};
    private final FileTypeHandlerConfiguration configuration;

    /**
     * @param configuration The file type handler configuration
     */
    public FileTypeHandler(FileTypeHandlerConfiguration configuration) {
        this.configuration = configuration;
    }

    @SuppressWarnings("MagicNumber")
    @Override
    public void handle(Object obj, HttpRequest<?> request, NettyMutableHttpResponse<?> response, ChannelHandlerContext context) {
        NettyFileCustomizableResponseType type;
        if (obj instanceof File) {
            type = new NettySystemFileCustomizableResponseType((File) obj);
        } else if (obj instanceof NettyFileCustomizableResponseType) {
            type = (NettyFileCustomizableResponseType) obj;
        } else if (obj instanceof SystemFileCustomizableResponseType) {
            type = new NettySystemFileCustomizableResponseType((SystemFileCustomizableResponseType) obj);
        } else if (obj instanceof StreamedFile) {
            type = new NettyStreamedFileCustomizableResponseType((StreamedFile) obj);
        } else if (obj instanceof SystemFile) {
            type = new NettySystemFileCustomizableResponseType((SystemFile) obj);
        } else {
            throw new CustomizableResponseTypeException("FileTypeHandler only supports File or FileCustomizableResponseType types");
        }

        long lastModified = type.getLastModified();

        // Cache Validation
        ZonedDateTime ifModifiedSince = request.getHeaders().getDate(HttpHeaders.IF_MODIFIED_SINCE);
        if (ifModifiedSince != null) {

            // Only compare up to the second because the datetime format we send to the client
            // does not have milliseconds
            long ifModifiedSinceDateSeconds = ifModifiedSince.toEpochSecond();
            long fileLastModifiedSeconds = lastModified / 1000;
            if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
                FullHttpResponse nettyResponse = notModified();
                context.writeAndFlush(nettyResponse);
                return;
            }
        }

        if (!response.getHeaders().contains(HttpHeaders.CONTENT_TYPE)) {
            response.header(HttpHeaders.CONTENT_TYPE, type.getMediaType().toString());
        }
        setDateAndCacheHeaders(response, lastModified);

        type.process(response);
        type.write(request, response, context);
        context.read();
    }

    @Override
    public boolean supports(Class<?> type) {
        return Arrays.stream(SUPPORTED_TYPES)
            .anyMatch((aClass -> aClass.isAssignableFrom(type)));
    }

    /**
     * @param filename The filename
     * @deprecated The media type now comes from the file types.
     * @see io.micronaut.http.server.types.files.FileCustomizableResponseType#getMediaType()
     * @return The {@link MediaType}
     */
    @Deprecated
    protected MediaType getMediaType(String filename) {
        return MediaType.TEXT_PLAIN_TYPE;
    }

    /**
     * @param response     The Http response
     * @param lastModified The last modified
     */
    protected void setDateAndCacheHeaders(MutableHttpResponse response, long lastModified) {
        // Date header
        MutableHttpHeaders headers = response.getHeaders();
        LocalDateTime now = LocalDateTime.now();
        headers.date(now);

        // Add cache headers
        LocalDateTime cacheSeconds = now.plus(configuration.getCacheSeconds(), ChronoUnit.SECONDS);
        if (response.header(HttpHeaders.EXPIRES) == null) {
            headers.expires(cacheSeconds);
        }

        if (response.header(HttpHeaders.CACHE_CONTROL) == null) {
            FileTypeHandlerConfiguration.CacheControlConfiguration cacheConfig = configuration.getCacheControl();
            StringBuilder header = new StringBuilder(cacheConfig.getPublic() ? "public" : "private");
            header.append(", max-age=");
            header.append(configuration.getCacheSeconds());
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

    private FullHttpResponse notModified() {
        NettyMutableHttpResponse response = (NettyMutableHttpResponse) HttpResponse.notModified();
        setDateHeader(response);
        return response.getNativeResponse();
    }

}
