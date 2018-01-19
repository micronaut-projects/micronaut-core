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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import org.particleframework.http.*;
import org.particleframework.http.HttpHeaders;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.server.netty.NettyHttpResponse;
import org.particleframework.http.server.netty.NettyHttpServer;
import org.particleframework.http.server.netty.async.DefaultCloseHandler;
import org.particleframework.http.server.netty.types.NettySpecialTypeHandler;
import org.particleframework.http.server.types.SpecialTypeHandlerException;
import org.particleframework.http.server.types.files.FileSpecialType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Responsible for writing files out to the response in Netty
 */
@Singleton
public class FileTypeHandler implements NettySpecialTypeHandler<Object> {


    private static final Logger LOG = LoggerFactory.getLogger(FileTypeHandler.class);
    private static final CompletableFuture<Map<String, String>> mediaTypeFileExtensions = CompletableFuture.supplyAsync(FileTypeHandler::loadMimeTypes);
    private static final String MIME_TYPES_FILE_NAME = "particle/http/mime.types";
    private final FileTypeHandlerConfiguration configuration;
    private final SimpleDateFormat dateFormat;

    public FileTypeHandler(FileTypeHandlerConfiguration configuration) {
        this.configuration = configuration;
        this.dateFormat = new SimpleDateFormat(configuration.getDateFormat(), Locale.US);
        this.dateFormat.setTimeZone(configuration.getDateTimeZone());
    }

    @Override
    public void handle(Object obj, HttpRequest request, NettyHttpResponse response, ChannelHandlerContext context) {
        
        File file;
        if (obj instanceof File) {
            file = (File) obj;
        } else if (obj instanceof FileSpecialType) {
            file = ((FileSpecialType) obj).getFile();
        } else {
            throw new SpecialTypeHandlerException("FileTypeHandler only supports File or FileSpecialType types");
        }


        // Cache Validation
        String ifModifiedSince = request.headers().get(HttpHeaders.IF_MODIFIED_SINCE);
        if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
            try {
                Date ifModifiedSinceDate = dateFormat.parse(ifModifiedSince);

                // Only compare up to the second because the datetime format we send to the client
                // does not have milliseconds
                long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
                long fileLastModifiedSeconds = file.lastModified() / 1000;
                if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
                    FullHttpResponse nettyResponse = notModified();
                    context.writeAndFlush(nettyResponse)
                            .addListener(new DefaultCloseHandler(context, request, nettyResponse));
                    return;
                }
            } catch (ParseException e) {
                //no-op
            }
        }

        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException e) {
            throw new SpecialTypeHandlerException("Could not find file", e);
        }

        long fileLength;
        try {
            fileLength = raf.length();
        } catch (IOException e) {
            throw new SpecialTypeHandlerException("Could not determine file length", e);
        }

        response.header(HttpHeaders.CONTENT_TYPE, getMediaType(file));
        response.header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileLength));
        setDateAndCacheHeaders(response, file);
        if (HttpUtil.isKeepAlive(request)) {
            response.header(HttpHeaders.CONNECTION, "keep-alive");
        }

        if (obj instanceof FileSpecialType) {
            ((FileSpecialType) obj).process(response);
        }

        FullHttpResponse nettyResponse = response.getNativeResponse();

        //The streams codec prevents non full responses from being written
        Optional.ofNullable(context.pipeline().get(NettyHttpServer.HTTP_STREAMS_CODEC))
                .ifPresent(handler -> context.pipeline().remove(handler));

        // Write the request data
        context.write(new DefaultHttpResponse(nettyResponse.protocolVersion(), nettyResponse.status(), nettyResponse.headers()), context.voidPromise());

        // Write the content.
        ChannelFuture flushFuture;
        if (context.pipeline().get(SslHandler.class) == null) {
            context.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength), context.newProgressivePromise());
            // Write the end marker.
            flushFuture = context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        } else {
            try {
                // HttpChunkedInput will write the end marker (LastHttpContent) for us.
                flushFuture = context.writeAndFlush(new HttpChunkedInput(new ChunkedFile(raf, 0, fileLength, 8192)),
                        context.newProgressivePromise());
            } catch (IOException e) {
                throw new SpecialTypeHandlerException("Could not read file", e);
            }
        }

        flushFuture.addListener(new DefaultCloseHandler(context, request, nettyResponse));
    }

    @Override
    public boolean supports(Class<?> type) {
        return File.class.isAssignableFrom(type) || FileSpecialType.class.isAssignableFrom(type);
    }

    protected MediaType getMediaType(File file) {
        try {
            String extension = getExtension(file);
            String contentType = mediaTypeFileExtensions.get().get(extension);
            if (contentType != null) {
                return new MediaType(contentType, extension);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            //no-op
        }

        return MediaType.TEXT_PLAIN_TYPE;
    }

    protected void setDateAndCacheHeaders(MutableHttpResponse response, File fileToCache) {
        // Date header
        Calendar time = new GregorianCalendar();
        response.header(HttpHeaders.DATE, dateFormat.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, configuration.getCacheSeconds());
        response.header(HttpHeaders.EXPIRES, dateFormat.format(time.getTime()));
        response.header(HttpHeaders.CACHE_CONTROL, "private, max-age=" + configuration.getCacheSeconds());
        response.header(
                HttpHeaderNames.LAST_MODIFIED, dateFormat.format(new Date(fileToCache.lastModified())));
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

    private String getExtension(File file) {
        String filename = file.getName();
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

    private static Map<String, String> loadMimeTypes() {
        InputStream is = null;
        try {
            is = FileTypeHandler.class.getClassLoader().getResourceAsStream(MIME_TYPES_FILE_NAME);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.US_ASCII));
            Map<String, String> result = new LinkedHashMap<>(100);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String formattedLine = line.trim().replaceAll("\\s{2,}", " ").replaceAll("\\s", "|");
                String[] tokens = formattedLine.split("\\|");
                for (int i = 1; i < tokens.length; i++) {
                    String fileExtension = tokens[i].toLowerCase(Locale.ENGLISH);
                    result.put(fileExtension, tokens[0]);
                }
            }
            return result;
        }
        catch (IOException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Failed to load mime types for file extension detection!");
            }
        }
        finally {
            if (is != null) {
                try {
                    is.close();
                }
                catch (IOException ignore) {
                }
            }
        }

        return Collections.emptyMap();
    }
}
