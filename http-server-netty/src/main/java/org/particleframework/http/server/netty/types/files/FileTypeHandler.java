/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.netty.types.files;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpRequest;
import org.particleframework.http.*;
import org.particleframework.http.HttpHeaders;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.server.netty.NettyHttpResponse;
import org.particleframework.http.server.netty.async.DefaultCloseHandler;
import org.particleframework.http.server.netty.types.NettyFileSpecialType;
import org.particleframework.http.server.netty.types.NettySpecialTypeHandler;
import org.particleframework.http.server.types.SpecialTypeHandlerException;
import org.particleframework.http.server.types.files.SystemFileSpecialType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Responsible for writing files out to the response in Netty
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
public class FileTypeHandler implements NettySpecialTypeHandler<Object> {


    private final FileTypeHandlerConfiguration configuration;
    private final SimpleDateFormat dateFormat;

    public FileTypeHandler(FileTypeHandlerConfiguration configuration) {
        this.configuration = configuration;
        this.dateFormat = new SimpleDateFormat(configuration.getDateFormat(), Locale.US);
        this.dateFormat.setTimeZone(configuration.getDateTimeZone());
    }

    @Override
    public void handle(Object obj, HttpRequest request, NettyHttpResponse response, ChannelHandlerContext context) {

        NettyFileSpecialType type;
        if (obj instanceof File) {
            type = new NettySystemFileSpecialType((File) obj);
        } else if (obj instanceof NettyFileSpecialType) {
            type = (NettyFileSpecialType) obj;
        } else if (obj instanceof SystemFileSpecialType) {
            type = new NettySystemFileSpecialType((SystemFileSpecialType) obj);
        } else {
            throw new SpecialTypeHandlerException("FileTypeHandler only supports File or FileSpecialType types");
        }

        long lastModified = type.getLastModified();

        // Cache Validation
        String ifModifiedSince = request.headers().get(HttpHeaders.IF_MODIFIED_SINCE);
        if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
            try {
                Date ifModifiedSinceDate = dateFormat.parse(ifModifiedSince);

                // Only compare up to the second because the datetime format we send to the client
                // does not have milliseconds
                long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
                long fileLastModifiedSeconds = lastModified / 1000;
                if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
                    FullHttpResponse nettyResponse = notModified();
                    context.writeAndFlush(nettyResponse)
                            .addListener(new DefaultCloseHandler(context, request, nettyResponse));
                    return;
                }
            } catch (ParseException | NumberFormatException e) {
                //no-op
            }
        }

        response.header(HttpHeaders.CONTENT_TYPE, getMediaType(type.getName()));
        setDateAndCacheHeaders(response, lastModified);
        if (HttpUtil.isKeepAlive(request)) {
            response.header(HttpHeaders.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }

        type.process(response);

        type.write(request, response, context);
    }

    @Override
    public boolean supports(Class<?> type) {
        return File.class.isAssignableFrom(type) || SystemFileSpecialType.class.isAssignableFrom(type);
    }

    protected MediaType getMediaType(String filename) {
        String extension = getExtension(filename);
        Optional<MediaType> mediaType = MediaType.forExtension(extension);
        return mediaType.orElse(MediaType.TEXT_PLAIN_TYPE);

    }

    protected void setDateAndCacheHeaders(MutableHttpResponse response, long lastModified) {
        // Date header
        Calendar time = new GregorianCalendar();
        response.header(HttpHeaders.DATE, dateFormat.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, configuration.getCacheSeconds());
        response.header(HttpHeaders.EXPIRES, dateFormat.format(time.getTime()));
        response.header(HttpHeaders.CACHE_CONTROL, "private, max-age=" + configuration.getCacheSeconds());
        response.header(
                HttpHeaderNames.LAST_MODIFIED, dateFormat.format(new Date(lastModified)));
    }

    protected void setDateHeader(MutableHttpResponse response) {
        Calendar time = new GregorianCalendar();
        response.header(HttpHeaders.DATE, dateFormat.format(time.getTime()));
    }

    private FullHttpResponse notModified() {
        NettyHttpResponse response = (NettyHttpResponse)HttpResponse.notModified();
        setDateHeader(response);
        return response.getNativeResponse();
    }

    private String getExtension(String filename) {
        int extensionPos = filename.lastIndexOf('.');
        int lastUnixPos = filename.lastIndexOf('/');
        int lastWindowsPos = filename.lastIndexOf('\\');
        int lastSeparator = Math.max(lastUnixPos, lastWindowsPos);

        int index = lastSeparator > extensionPos ? -1 : extensionPos;
        if (index == -1) {
            return "";
        } else {
            return filename.substring(index + 1);
        }
    }

}
