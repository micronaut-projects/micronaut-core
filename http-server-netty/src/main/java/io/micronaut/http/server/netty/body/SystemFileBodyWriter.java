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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.exceptions.MessageBodyException;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.netty.body.NettyBodyWriter;
import io.micronaut.http.netty.body.NettyWriteContext;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.types.files.SystemFile;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import jakarta.inject.Singleton;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

import static io.micronaut.http.HttpHeaders.CONTENT_RANGE;

/**
 * Body writer for {@link SystemFile}s.
 *
 * @author Graeme Rocher
 * @since 4.0.0
 */
@Singleton
@Experimental
@Internal
public final class SystemFileBodyWriter extends AbstractFileBodyWriter implements NettyBodyWriter<SystemFile> {
    private static final String UNIT_BYTES = "bytes";

    public SystemFileBodyWriter(NettyHttpServerConfiguration.FileTypeHandlerConfiguration configuration) {
        super(configuration);
    }

    @Override
    public void writeTo(HttpRequest<?> request, MutableHttpResponse<SystemFile> outgoingResponse, Argument<SystemFile> type, MediaType mediaType, SystemFile object, NettyWriteContext nettyContext) throws CodecException {
        writeTo(request, outgoingResponse, object, nettyContext);
    }

    @Override
    public void writeTo(Argument<SystemFile> type, MediaType mediaType, SystemFile file, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        throw new UnsupportedOperationException("Can only be used in a Netty context");
    }

    public void writeTo(HttpRequest<?> request, MutableHttpResponse<SystemFile> response, SystemFile systemFile, NettyWriteContext nettyContext) throws CodecException {
        if (response instanceof NettyMutableHttpResponse<?> nettyResponse) {
            if (!systemFile.getFile().canRead()) {
                throw new MessageBodyException("Could not find file");
            }
            if (handleIfModifiedAndHeaders(request, response, systemFile, nettyResponse)) {
                nettyContext.writeFull(notModified(response));
            } else {

                // Parse the range headers (if any), and determine the position and content length
                // Only `bytes` ranges are supported. Only single ranges are supported. Invalid ranges fall back to returning the full response.
                // See https://httpwg.org/specs/rfc9110.html#field.range
                long fileLength = systemFile.getLength();
                long position = 0;
                long contentLength = fileLength;
                if (fileLength > -1) {
                    String rangeHeader = request.getHeaders().get(HttpHeaders.RANGE);
                    if (rangeHeader != null
                        && request.getMethod() == HttpMethod.GET // A server MUST ignore a Range header field received with a request method that is unrecognized or for which range handling is not defined.
                        && rangeHeader.startsWith(UNIT_BYTES) // An origin server MUST ignore a Range header field that contains a range unit it does not understand.
                        && response.status() == HttpStatus.OK // The Range header field is evaluated after evaluating the precondition header fields defined in Section 13.1, and only if the result in absence of the Range header field would be a 200 (OK) response.
                    ) {
                        IntRange range = parseRangeHeader(rangeHeader, fileLength);
                        if (range != null // A server that supports range requests MAY ignore or reject a Range header field that contains an invalid ranges-specifier (Section 14.1.1)
                            && range.firstPos < range.lastPos // A server that supports range requests MAY ignore a Range header field when the selected representation has no content (i.e., the selected representation's data is of zero length).
                            && range.firstPos < fileLength
                            && range.lastPos < fileLength
                        ) {
                            position = range.firstPos;
                            contentLength = range.lastPos + 1 - range.firstPos;
                            response.status(HttpStatus.PARTIAL_CONTENT);
                            response.header(CONTENT_RANGE, "%s %d-%d/%d".formatted(UNIT_BYTES, range.firstPos, range.lastPos, fileLength));
                        }
                    }
                    response.header(HttpHeaders.ACCEPT_RANGES, UNIT_BYTES);
                    response.header(HttpHeaders.CONTENT_LENGTH, Long.toString(contentLength));
                } else {
                    response.header(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                }

                // Write the request data
                final DefaultHttpResponse finalResponse = new DefaultHttpResponse(nettyResponse.getNettyHttpVersion(), nettyResponse.getNettyHttpStatus(), nettyResponse.getNettyHeaders());
                writeFile(systemFile, nettyContext, position, contentLength, finalResponse);
            }
        } else {
            throw new IllegalArgumentException("Unsupported response type. Not a Netty response: " + response);
        }
    }

    private static void writeFile(SystemFile systemFile, NettyWriteContext context, long position, long contentLength, DefaultHttpResponse finalResponse) {
        // Write the content.
        File file = systemFile.getFile();
        RandomAccessFile randomAccessFile = open(file);

        context.writeFile(
            finalResponse,
            randomAccessFile,
            position,
            contentLength
        );
    }

    private static RandomAccessFile open(File file) {
        try {
            return new RandomAccessFile(file, "r");
        } catch (FileNotFoundException e) {
            throw new MessageBodyException("Could not find file", e);
        }
    }

    @Nullable
    private static IntRange parseRangeHeader(String value, long contentLength) {
        int equalsIdx = value.indexOf('=');
        if (equalsIdx < 0 || equalsIdx == value.length() - 1) {
            return null; // Malformed range
        }

        int minusIdx = value.indexOf('-', equalsIdx + 1);
        if (minusIdx < 0) {
            return null; // Malformed range
        }

        String from = value.substring(equalsIdx + 1, minusIdx).trim();
        String to = value.substring(minusIdx + 1).trim();
        try {
            long fromPosition = from.isEmpty() ? 0 : Long.parseLong(from);
            long toPosition = to.isEmpty() ? contentLength - 1 : Long.parseLong(to);
            return new IntRange(fromPosition, toPosition);
        } catch (NumberFormatException e) {
            return null; // Malformed range
        }
    }

    // See https://httpwg.org/specs/rfc9110.html#rule.int-range
    private static class IntRange {
        private final long firstPos;
        private final long lastPos;

        IntRange(long firstPos, long lastPos) {
            this.firstPos = firstPos;
            this.lastPos = lastPos;
        }
    }

}
